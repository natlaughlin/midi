package com.natlaughlin.midi;

import java.io.DataOutputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import javax.sound.sampled.Port.Info;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.lang.SerializationUtils;
import org.joda.time.DateTime;


public class MidiSender extends Thread
{
	private CommandLine cli;
	private DumpReceiver dumpReceiver;
	
	private boolean debug;
	private String hostName;
	private String deviceName;
	private int socketPort;
	private DateTime lastEvent;
	
	private MidiDevice device;
	private Socket socket;
	
	private Timer timer;
	
	private static int TIMEOUT = 10000;
	
	public static void main(String[] args) throws Exception
	{
		MidiSender mc = new MidiSender();
		mc.parseOptions(args);
		mc.start();
		
	}	
	
	public MidiSender()
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
		
		Option printMidi = new Option("list", "Show all Midi devices");
		options.addOption(printMidi);
		
		Option device = OptionBuilder.withArgName("name").hasArg()
				.withDescription("Use this MIDI device as transmitter")
				.create("device");
		options.addOption(device);
		
		Option host = OptionBuilder.withArgName("name").hasArg()
				.withDescription("Connect to this host name to send MIDI events")
				.create("host");
		options.addOption(host);
		
		Option port = OptionBuilder.withArgName(String.valueOf(Midi.PORT)).hasArg()
				.withDescription("Socket port to send MIDI events")
				.create("port");
		options.addOption(port);

		CommandLineParser parser = new PosixParser();
		cli = parser.parse(options, args);
		
		debug = false;
		if(cli.hasOption("debug"))
		{
			debug = true;
		}	
		
		if(cli.hasOption("help"))
		{
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("MidiSender", options );
			stop();
		}
		
		if(cli.hasOption("list"))
		{
			listTransmitterDevices();
			stop();
		}
		
		deviceName = "";
		if(cli.hasOption("device"))
		{
			deviceName = cli.getOptionValue("device");
		}
		
		hostName = Midi.HOST;
		if(cli.hasOption("host"))
		{
			hostName = cli.getOptionValue("host");
		}
		
		socketPort = Midi.PORT;
		if(cli.hasOption("port"))
		{
			socketPort = Integer.parseInt(cli.getOptionValue("port"));
		}
		
		

	}
	
	private void startTimer()
	{
		if(timer != null)
			timer.cancel();
		
		timer = new Timer();
		
		final MidiSender midiSender = this;
		
		timer.scheduleAtFixedRate(new TimerTask()
		{
			@Override
			public void run()
			{

				log(String.format("Last MIDI event: %s", lastEvent)); 
			
			}
		}, Midi.SENDER_POLL_MILLISECONDS, Midi.SENDER_POLL_MILLISECONDS);
	}
	
	public void run()
	{
		
		try
		{
			startTimer();
			openMidiDevice();
			openSocket();
			transmitEvents();
		}
		catch(Exception e)
		{
			e.printStackTrace();
			log("MidiSender restarting");
			try
			{
				sleep(TIMEOUT);
				run();
			}
			catch(Exception ex)
			{
				ex.printStackTrace();
				stop();
			}
		}
		

	}
		
	public void openMidiDevice() throws Exception
	{

		log("Opening device matching: " + deviceName);
		
		device = getInputDeviceByName(deviceName);
		device.open();
		
		log("Transmitter device opened");
		
		
	}
	
	public void openSocket() throws Exception
	{
		log(String.format("Connecting to MidiReceiver %s %s", hostName, String.valueOf(socketPort)));
		
		if(socket != null)
		{
			socket.close();
		}
		
		socket = new Socket(hostName,socketPort);
		
		if(socket == null)
		{
			throw new Exception("Can't connect to socket");
		}

		log("Connected to MidiReceiver");
		
	}
	
	public void transmitEvents() throws Exception
	{
		if(device == null)
		{
			throw new Exception("Device not available");
		}
		
		if(socket == null)
		{
			throw new Exception("Socket not connected");
		}
		
		Transmitter t = device.getTransmitter();
		ObjectReceiver receiver = new ObjectReceiver();
		receiver.setMidiSender(this);
		receiver.setOutputStream(socket.getOutputStream());
		t.setReceiver(receiver);
		
		log("Transmitting MIDI events");
	}
	
	public class ObjectReceiver implements Receiver
	{
		private ObjectOutputStream oos;
		private MidiSender midiSender;
		
		public void setMidiSender(MidiSender value)
		{
			midiSender = value;
		}

		public void setOutputStream(OutputStream value) throws Exception
		{
			oos = new ObjectOutputStream(value);
		}
		
		@Override
		public void close()
		{
			
		}

		@Override
		public void send(MidiMessage message, long timeStamp)
		{
			lastEvent = new DateTime();
			
			if(debug)
			{
				dumpReceiver.send(message, timeStamp);
			}
			
			if(message.getMessage().length < 2)
				return;
			
			try
			{ 
				MidiEventSerializable mes = new MidiEventSerializable(message, timeStamp);
				
				oos.writeObject(mes);
			}
			catch(Exception e)
			{
				e.printStackTrace();
				log("MidiSender restarting");
				midiSender.run();
			}
		}
		
	}
	
	public void listTransmitterDevices() throws MidiUnavailableException
	{
		MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
		for (int i = 0; i < infos.length; i++)
		{
			MidiDevice device = MidiSystem.getMidiDevice(infos[i]);
			if (device.getMaxTransmitters() != 0)
			{
				String dName = device.getDeviceInfo().getName();
				String description = device.getDeviceInfo().getDescription();
				String vendor = device.getDeviceInfo().getVendor();
				String version = device.getDeviceInfo().getVersion();
				System.out.println(String.format("%s\t%s\t%s\t%s", dName, description, vendor, version));
			}
		}
	}
	
	public MidiDevice getInputDeviceByName(String deviceName) throws MidiUnavailableException
	{
		MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
		for (int i = 0; i < infos.length; i++)
		{
			MidiDevice device = MidiSystem.getMidiDevice(infos[i]);
			if (device.getMaxTransmitters() != 0
					&& device.getDeviceInfo().getName().contains(deviceName))
			{
				if(debug)
				{
					log("Found device " + device.getDeviceInfo().getName().toString());
				}
				return device;
			}
		}
		return null;
	}
	
	private void log(String message)
	{
		if(debug)
		{
			DateTime now = new DateTime();
			System.out.println(String.format("%s\t%s", now, message));
		}
	}
	
	public void error(Exception e) throws Exception
	{
		throw new Exception(e);
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
