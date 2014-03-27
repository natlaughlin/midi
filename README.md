#MIDI

This project contains clients/servers for sending MIDI keyboard events over sockets.
The receiver classes record MIDI data from these events and flushes the resulting MIDI file to disk after a specified time.

## Compile

Get [Maven](http://maven.apache.org/), then execute

```java
mvn assembly:assembly
```

## Examples

### Single MIDI Receiver and Sender

Plug your MIDI keyboard into the system (via USB for example), and find the list of devices:
```java
java -cp target/midi-0.0.1-SNAPSHOT-jar-with-dependencies.jar com.natlaughlin.midi.MidiSender -list
```
```
VMPK Output  VMPK Output  Unknown vendor	Unknown version
Real Time Sequencer	Software sequencer	Oracle Corporation	Version 1.0
```

Start the MIDI receiver listening for events on port 20070:
```java
java -cp target/midi-0.0.1-SNAPSHOT-jar-with-dependencies.jar com.natlaughlin.midi.MidiReceiver -debug
```
```
2013-02-16T13:50:29.106-08:00  MidiReceiver waiting for connection on socket 20070
```

Start the MIDISender with the MIDI device (VMPK Virtual Keyboard) to send MIDI data to port 20070:
```java
java -cp target/midi-0.0.1-SNAPSHOT-jar-with-dependencies.jar com.natlaughlin.midi.MidiSender -device VMPK -debug
```
```
2013-02-16T13:50:52.932-08:00  Opening device matching: VMPK
2013-02-16T13:50:53.157-08:00	Found device VMPK Output
2013-02-16T13:50:53.159-08:00	Transmitter device opened
2013-02-16T13:50:53.160-08:00	Connecting to MidiReceiver localhost 20070
2013-02-16T13:50:53.181-08:00	Connected to MidiReceiver
2013-02-16T13:50:53.188-08:00	Transmitting MIDI events
```

Play some notes on the keyboard and the MIDI events will be displayed on both sender and receiver:
```
2013-02-16T13:51:14.191-08:00  timestamp 55751890229 us: [90 3C 62] channel 1: note On C4 velocity: 98
2013-02-16T13:51:14.285-08:00	timestamp 55751985941 us: [80 3C 00] channel 1: note Off C4 velocity: 0
2013-02-16T13:51:14.382-08:00	timestamp 55752082400 us: [90 3E 62] channel 1: note On D4 velocity: 98
2013-02-16T13:51:14.463-08:00	timestamp 55752161860 us: [80 3E 00] channel 1: note Off D4 velocity: 0
2013-02-16T13:51:14.574-08:00	timestamp 55752274471 us: [90 40 62] channel 1: note On E4 velocity: 98
```

After playing your song, stop pressing anything for 10 seconds and the MIDI receiver will write your MIDI file.
```
2013-02-16T13:51:26.613-08:00  Writing: /Users/nlaughlin/Desktop/workspace/midi/midi/midi_20130216135114234.mid
```

### Multiple MIDI Receivers and Senders

To connect more than one Sender and Receiver to a single port (Multicast UDP), use the MidiMulticastSender and MidiMulticastReceiver in tandem.

Start the MIDI Multicast sender with the MIDI device (VMPK Virtual Keyboard) to send MIDI data to port 20070:
```java
java -cp target/midi-0.0.1-SNAPSHOT-jar-with-dependencies.jar com.natlaughlin.midi.MidiMulticastSender -device VMPK -debug
```
```
2013-02-13T16:37:01.302-08:00  Opening device matching: VMPK
2013-02-13T16:37:01.511-08:00	Found device VMPK Output
2013-02-13T16:37:01.512-08:00	Transmitter device opened
2013-02-13T16:37:01.516-08:00	Connecting to MidiMulticastServer group 225.0.0.50 20070
2013-02-13T16:37:01.520-08:00	Connected to MidiMulticastServer group
2013-02-13T16:37:01.521-08:00	Transmitting MIDI events
```

Start the MIDI Multicast receiver listening for events on port 20070:
```java
java -cp target/midi-0.0.1-SNAPSHOT-jar-with-dependencies.jar com.natlaughlin.midi.MidiMulticastReceiver -debug
```
```
2013-02-13T16:37:57.787-08:00  MidiMulticastReceiver joining Multicast group 225.0.0.50 20070
2013-02-13T16:37:57.884-08:00	MidiMulticastReceiver listening.
```

Start another MIDI Multicast receiver listening for events on port 20070:
```java
java -cp target/midi-0.0.1-SNAPSHOT-jar-with-dependencies.jar com.natlaughlin.midi.MidiMulticastReceiver -debug
```
```
2013-02-13T16:37:57.787-08:00  MidiMulticastReceiver joining Multicast group 225.0.0.50 20070
2013-02-13T16:37:57.884-08:00  MidiMulticastReceiver listening.
```

Play some notes on the keyboard and the MIDI events will be displayed on the sender and both receivers:
```
2013-02-13T16:38:34.753-08:00  timestamp 22881485690 us: [80 40 00] channel 1: note Off E4 velocity: 0
2013-02-13T16:38:34.810-08:00	timestamp 22881541807 us: [80 41 00] channel 1: note Off F4 velocity: 0
2013-02-13T16:38:34.914-08:00	timestamp 22881646098 us: [90 43 62] channel 1: note On G4 velocity: 98
2013-02-13T16:38:35.001-08:00	timestamp 22881733805 us: [80 43 00] channel 1: note Off G4 velocity: 0
```

After playing your song, stop pressing anything for 10 seconds and both MIDI receivers will write your MIDI file.
```
2013-02-13T16:38:47.773-08:00  Writing: /Users/nlaughlin/Desktop/workspace/midi/midi/midi_20130213163834502.mid
```

### SSH Tunnel

If your MidiReceiver machine is behind a firewell, you can connect them with an SSH tunnel.  

```
ssh -f -g username@host.com -L 20070:localhost:20070 -N
```

or

```
autossh -M 20000 -f -g username@host.com -L 20070:127.0.0.1:20070 -N
```
