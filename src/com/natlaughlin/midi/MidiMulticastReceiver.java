package com.natlaughlin.midi;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Timer;
import java.util.TimerTask;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import javax.sound.midi.Track;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.SerializationUtils;
import org.joda.time.DateTime;
import org.joda.time.Seconds;

import com.natlaughlin.midi.DumpReceiver;

public class MidiMulticastReceiver extends Thread
{

	private boolean debug;
	
	private CommandLine cli;
	
	private String prefix;
	private SimpleDateFormat dateFormat;
	private String directory;
	
	private int socketPort;
	private String hostName;
	private InetAddress multiGroup;
	private MulticastSocket multiSocket;

	private DateTime lastEvent;
	private DumpReceiver dumpReceiver;
	private Sequence sequence;
	private Track track;
	private long startTick;
	private int secondsToWait;
	private File midiFile;


	private ServerSocket ss;

	public static void main(String[] args) throws Exception
	{
		MidiMulticastReceiver ms = new MidiMulticastReceiver();
		ms.parseOptions(args);
		ms.start();
	}
	
	public MidiMulticastReceiver()
	{
		dumpReceiver = new DumpReceiver(new LogPrintStream(System.out));
	}

	private void parseOptions(String[] args) throws Exception
	{
		Options options = new Options();
		Option help = new Option("help", "Print this message");
		options.addOption(help);
		Option dbg = new Option("debug", "Print debugging information");
		options.addOption(dbg);
		
		Option host = OptionBuilder.withArgName(MidiMulticast.HOST).hasArg()
				.withDescription("Connect to this Multicast host to receive MIDI events")
				.create("host");
		options.addOption(host);
		
		Option port = OptionBuilder.withArgName(String.valueOf(MidiMulticast.PORT)).hasArg()
				.withDescription("Socket port to receive MIDI events")
				.create("port");
		options.addOption(port);
		
		Option wait = OptionBuilder.withArgName(String.valueOf(Midi.WAIT_SECONDS)).hasArg()
				.withDescription("Wait this many seconds after the last MIDI event to write the MIDI file")
				.create("wait");
		options.addOption(wait);
		
		Option pfx = OptionBuilder.withArgName(Midi.FILE_PREFIX).hasArg()
				.withDescription("MIDI output filename prefix [dir]/[prefix][dateformat].mid")
				.create("prefix");
		options.addOption(pfx);
		
		Option dir = OptionBuilder.withArgName(Midi.FILE_DIR).hasArg()
				.withDescription("MIDI output filename directory [dir]/[prefix][dateformat].mid")
				.create("dir");
		options.addOption(dir);
		
		Option df = OptionBuilder.withArgName(Midi.FILE_DATEFORMAT).hasArg()
				.withDescription("MIDI output filename date format [dir]/[prefix][dateformat].mid")
				.create("dateformat");
		options.addOption(df);

		CommandLineParser parser = new PosixParser();
		cli = parser.parse(options, args);
		
		if(cli.hasOption("help"))
		{
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("MidiMulticastReceiver", options );
			stop();
		}
		
		debug = false;
		if(cli.hasOption("debug"))
		{
			debug = true;
		}	
		
		hostName = MidiMulticast.HOST;
		if(cli.hasOption("host"))
		{
			hostName = cli.getOptionValue("host");
		}
		
		socketPort = MidiMulticast.PORT;
		if(cli.hasOption("port"))
		{
			socketPort = Integer.parseInt(cli.getOptionValue("port"));
		}
		
		secondsToWait = Midi.WAIT_SECONDS;
		if(cli.hasOption("wait"))
		{
			secondsToWait = Integer.parseInt(cli.getOptionValue("wait"));
		}
		
		directory = Midi.FILE_DIR;
		if(cli.hasOption("dir"))
		{
			directory = cli.getOptionValue("dir");
		}
		
		prefix = Midi.FILE_PREFIX;
		if(cli.hasOption("prefix"))
		{
			prefix = cli.getOptionValue("prefix");
		}
		
		dateFormat = new SimpleDateFormat(Midi.FILE_DATEFORMAT);
		if(cli.hasOption("dateformat"))
		{
			dateFormat = new SimpleDateFormat(cli.getOptionValue("dateformat"));
		}

	}

	private void startTimer()
	{
		Timer timer = new Timer();
		
		timer.scheduleAtFixedRate(new TimerTask()
		{
			@Override
			public void run()
			{

				log(String.format("Last MIDI event: %s", lastEvent)); 
				
				DateTime now = new DateTime();

				int secondsSinceLastEvent = 0;
				if(lastEvent != null)
				{
					secondsSinceLastEvent = Seconds.secondsBetween(lastEvent,now).getSeconds();
				}
				
				if (midiFile != null && secondsSinceLastEvent > secondsToWait)
				{
					try
					{
						writeMidiFile();
					} 
					catch (Exception e)
					{
						e.printStackTrace();
					}
				}

			}
		}, Midi.RECEIVER_POLL_MILLISECONDS, Midi.RECEIVER_POLL_MILLISECONDS);
	}

	public void run()
	{
		startTimer();

		while (true)
		{
			try
			{

				log(String.format("MidiMulticastReceiver joining Multicast group %s %s", hostName, socketPort));
				
				multiGroup = InetAddress.getByName(hostName);
				multiSocket = new MulticastSocket(socketPort);
				multiSocket.joinGroup(multiGroup);

				log("MidiMulticastReceiver listening.");
				
				while (multiSocket.isBound())
				{

					byte[] bytes = new byte[1024];
					DatagramPacket pack = new DatagramPacket(bytes, bytes.length);
					multiSocket.receive(pack);
					
					MidiEventSerializable me = (MidiEventSerializable) SerializationUtils.deserialize(pack.getData());

					processEvent(me);
				}

			} catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}

	private void writeMidiFile() throws Exception
	{
		if (midiFile != null)
		{
			log("Writing: " + midiFile.getAbsolutePath());
			
			MidiSystem.write(sequence, 0, midiFile);
			midiFile = null;
		}
	}

	private void processEvent(MidiEventSerializable me) throws Exception
	{

		lastEvent = new DateTime();

		if (midiFile == null)
		{
			String filename = String.format("%s%s.mid", prefix,  dateFormat.format(lastEvent.toDate()));
			midiFile = new File(directory, filename);
			FileUtils.touch(midiFile);
			sequence = new Sequence(Sequence.PPQ, 5000);
			track = sequence.createTrack();
			startTick = me.getTick();

			// 120 BPM
			final int TEMPO = 0x51;
			int tempoInMPQ = 500000;
			byte[] data = new byte[3];
			data[0] = (byte) ((tempoInMPQ >> 16) & 0xFF);
			data[1] = (byte) ((tempoInMPQ >> 8) & 0xFF);
			data[2] = (byte) (tempoInMPQ & 0xFF);
			MetaMessage message = new MetaMessage();
			message.setMessage(TEMPO, data, data.length);
			MidiEvent event = new MidiEvent(message, 0);
			track.add(event);

		}

		// figure out what kind of message it is.
		MidiMessage msg = null;
		int mt = me.getMessageType();
		byte[] b = me.getMessage();
		if (MidiEventSerializable.SHORT == mt)
		{
			ShortMessage m = new ShortMessage();
			if(b.length == 4)
			{
				m.setMessage(b[0], b[1], b[2], b[3]);
			}
			else
			{
				m.setMessage(b[0], b[1], b[2]);
			}
			msg = m;
		} 
		else if (MidiEventSerializable.SYSEX == mt)
		{
			SysexMessage m = new SysexMessage();
			m.setMessage(b, b.length);
			msg = m;
		} 
		else if (MidiEventSerializable.META == mt)
		{
			MetaMessage m = new MetaMessage();
			m.setMessage(me.getMetaType(), b, b.length);
			msg = m;
		}

		if(debug)
		{
			dumpReceiver.send(msg, me.getTick());
		}

		MidiEvent e = new MidiEvent(msg, (me.getTick() - startTick) / 100);

		track.add(e);

	}
	
	private void log(String message)
	{
		if(debug)
		{
			DateTime now = new DateTime();
			System.out.println(String.format("%s\t%s", now, message));
		}
	}
	
	public class LogPrintStream extends PrintStream
	{

		public LogPrintStream(OutputStream out)
		{
			super(out);
		}
		
		@Override
		public void println(String message) 
		{
			log(message);
		}
		
	}


}
