package com.natlaughlin.midi;


import java.io.Serializable;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;

public class MidiEventSerializable implements Serializable
{

	public static int SHORT = 1;
	public static int SYSEX = 2;
	public static int META = 3;
	
	private long tick;
	private byte[] message;
	private int messageType;
	private int metaType;
	
	public MidiEventSerializable(MidiMessage message, long tick)
	{
		
		
		this.tick = tick;
		//this.message = message.getMessage();
		
		byte[] bytes = null;
		
		if(message instanceof ShortMessage)
		{
			ShortMessage sm = (ShortMessage) message;
			messageType = SHORT;
			if(sm.getMessage().length > 1)
			{
				bytes = sm.getMessage();
			}

		}
		else if(message instanceof SysexMessage)
		{
			SysexMessage sm = (SysexMessage) message;
			bytes = sm.getData();
			messageType = SYSEX;
		}
		else if(message instanceof MetaMessage)
		{
			MetaMessage mm = (MetaMessage) message;
			bytes = mm.getData();
			messageType = META;
			metaType = ((MetaMessage) message).getType();
		}
		
		this.message = bytes;
	}

	public long getTick()
	{
		return tick;
	}

	public byte[] getMessage()
	{
		return message;
	}
	
	public int getMessageType()
	{
		return messageType;
	}
	
	public int getMetaType()
	{
		return metaType;
	}

	
}
