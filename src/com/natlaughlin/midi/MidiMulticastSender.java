package com.natlaughlin.midi;

import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Timer;
import java.util.TimerTask;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.lang.SerializationUtils;
import org.joda.time.DateTime;
import org.joda.time.Seconds;


public class MidiMulticastSender extends Thread
{
	private CommandLine cli;
	private DumpReceiver dumpReceiver;
	
	private boolean debug;
	private String hostName;
	private String deviceName;
	private int socketPort;
	private DateTime lastEvent;
	
	public static void main(String[] args) throws Exception
	{
		MidiMulticastSender mc = new MidiMulticastSender();
		mc.parseOptions(args);
		mc.start();
		
	}	
	
	public MidiMulticastSender()
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
		
		Option printMidi = new Option("list", "Show all MIDI devices");
		options.addOption(printMidi);
		
		Option device = OptionBuilder.withArgName("name").hasArg()
				.withDescription("Use this MIDI device as transmitter")
				.create("device");
		options.addOption(device);
		
		Option host = OptionBuilder.withArgName(String.valueOf(MidiMulticast.HOST)).hasArg()
				.withDescription("Connect to this Multicast group to send MIDI events")
				.create("host");
		options.addOption(host);
		
		Option port = OptionBuilder.withArgName(String.valueOf(MidiMulticast.PORT)).hasArg()
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
			formatter.printHelp("MidiMulticastSender", options );
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

			}
		}, Midi.SENDER_POLL_MILLISECONDS, Midi.SENDER_POLL_MILLISECONDS);
	}
	
	public void run()
	{
		
		startTimer(); 
		
		try
		{
			openMidiDevice();
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}

	}
		
	public void openMidiDevice() throws Exception
	{

		log("Opening device matching: " + deviceName);
		
		MidiDevice device = getInputDeviceByName(deviceName);
		device.open();
		
		log("Transmitter device opened");
		
		boolean connected = false;
		
		InetAddress group = InetAddress.getByName(hostName);
		MulticastSocket socket = null;
		while(!connected)
		{

			log(String.format("Connecting to MidiMulticastServer group %s %s",hostName, socketPort));
			
			try
			{
				socket = new MulticastSocket(socketPort);
				if(socket != null)
				{
					connected = true;
				}
			}
			catch(Exception e)
			{
				log(String.format("Can't create Multicast socket %s %s", hostName, socketPort) );
				Thread.currentThread().sleep(10000);
			}
		}
		
		log("Connected to MidiMulticastServer group");
		
		Transmitter t = device.getTransmitter();
		ObjectReceiver receiver = new ObjectReceiver();

		receiver.setGroup(group);
		receiver.setSocket(socket);
		t.setReceiver(receiver);
		
		log("Transmitting MIDI events");
	}
	
	public class ObjectReceiver implements Receiver
	{
		private ObjectOutputStream oos;
		private InetAddress group;
		private MulticastSocket socket;

		public void setGroup(InetAddress value)
		{
			group = value;
		}
		
		public void setSocket(MulticastSocket value)
		{
			socket = value;
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
				
				byte[] bytes = SerializationUtils.serialize(mes);
				DatagramPacket pack = new DatagramPacket(bytes, bytes.length,
						 group, socket.getLocalPort());
				socket.send(pack);
				
				//oos.writeObject(mes);
			}
			catch(Exception e)
			{
				e.printStackTrace();
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
