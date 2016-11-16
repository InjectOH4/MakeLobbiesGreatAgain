import java.awt.AWTException;
import java.awt.GridLayout;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.UnsupportedLookAndFeelException;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PcapAddress;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.PcapNetworkInterface.PromiscuousMode;
import org.pcap4j.core.Pcaps;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.UdpPacket;
import org.pcap4j.packet.namednumber.IpNumber;

public class Boot {
	private static InetAddress addr = null;
	
	public static void main(String[] args){
		try {
			setupTray();
			
			for(PcapNetworkInterface i : Pcaps.findAllDevs()){
				for(PcapAddress x : i.getAddresses()){
					if(x.getBroadcastAddress() != null && x.getBroadcastAddress().toString().equals("/0.0.0.0")){
							addr = x.getAddress();
					}
				}
			}
			
			if(addr == null){
				getLocalAddr();
			}
			PcapNetworkInterface nif = Pcaps.getDevByAddress(addr);
			System.out.println("Operating on device with MAC: " + nif.getLinkLayerAddresses().get(0));

			int snapLen = 65536;
			PromiscuousMode mode = PromiscuousMode.PROMISCUOUS;
			int timeout = 0;
			PcapHandle handle = nif.openLive(snapLen, mode, timeout);

			short pckCount = 0;
			long lastPacketTime = 0;
			String currSrv = null;
			Overlay ui = new Overlay();

			while(true){
				Packet packet = handle.getNextPacket();
				
				if(packet != null){
					IpV4Packet ippacket = packet.get(IpV4Packet.class);
					
					if(ippacket != null){
						if(ippacket.getHeader().getProtocol() == IpNumber.UDP && !ippacket.getHeader().getSrcAddr().isSiteLocalAddress()){
							UdpPacket udppack = ippacket.get(UdpPacket.class);
							String srcAddrStr = ippacket.getHeader().getSrcAddr().toString(); // Shows as '/0.0.0.0'

							if(udppack.getPayload().getRawData().length == 4){ //Leave/Join packet is always payload length 4
								if(lastPacketTime == 0 || System.currentTimeMillis() - lastPacketTime < 10000){
									pckCount++;

									if(pckCount == 3){ //3 of the leave/join packet are always sent in rapid succession	
										if(!srcAddrStr.equals(currSrv)){ //TODO: Bugfix - Joining same killer twice won't trigger a lookup
											geolocate(srcAddrStr, ui);
											currSrv = srcAddrStr; //This serves to prevent seeing the message upon joining then leaving
										}
										pckCount = 0;
									}
								}else{ //If the packets take more than 10 secs, probably wrong packet
									lastPacketTime = 0;
									pckCount = 0;
								}
							}
						}
					}
				}
			}
		} catch (PcapNativeException | NotOpenException 
				| ClassNotFoundException | InstantiationException 
				| IllegalAccessException | UnsupportedLookAndFeelException 
				| IOException | ParseException 
				| AWTException | InterruptedException e) {
			e.printStackTrace();
		}

	}
	
	public static void setupTray() throws AWTException{
		final SystemTray tray = SystemTray.getSystemTray();
		
		ActionListener listener = new ActionListener() {
	        public void actionPerformed(ActionEvent e) {
	            System.exit(0);
	        }
	    };
	    PopupMenu popup = new PopupMenu();
	    MenuItem exit = new MenuItem();
	    exit.addActionListener(listener);
	    exit.setLabel("Exit");
	    popup.add(exit);
		tray.add(new TrayIcon(Toolkit.getDefaultToolkit().getImage("icon.png"), "MLGA", popup));
	}
	
	public static void geolocate(String ip, Overlay ui) throws IOException, ParseException{
		String code = null;
		URL url = new URL("http://freegeoip.net/json" + ip);

		try (InputStream is = url.openStream();
				BufferedReader buf = new BufferedReader(new InputStreamReader(is))) {
			JSONParser parser = new JSONParser();
			JSONObject obj = (JSONObject)parser.parse(buf);
			code = (String)obj.get("country_code");
		}
		ui.setKillerLocale(code);
	}
	
	public static void getLocalAddr() throws InterruptedException{
		JFrame frame = new JFrame("MLGA Network Device Locate");
		frame.setFocusableWindowState(true);
		
		JLabel ipLab = new JLabel("Enter LAN IP:", JLabel.LEFT);
		JLabel exLab = new JLabel("(Ex. 192.168.0.2 or 10.0.0.2, obtained from Network Settings)", JLabel.LEFT);
		JTextField lanIP = new JTextField();
		lanIP.setSize(200, 20);
		JButton start = new JButton("Start");
		start.addActionListener(new ActionListener(){
			public void actionPerformed(ActionEvent e){
				try {
					addr = InetAddress.getByName(lanIP.getText());
					frame.setVisible(false);
					frame.dispose();
				} catch (UnknownHostException e1) {
					e1.printStackTrace();
				}
			}
		});
		
		frame.setLayout(new GridLayout(4,1));
		frame.add(ipLab);
		frame.add(exLab);
		frame.add(lanIP);
		frame.add(start);
		frame.setAlwaysOnTop(true);
		frame.pack();
		frame.setLocation(5, 420);
		frame.setSize(400, 150);
		frame.setVisible(true);
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		
		while(frame.isVisible()){
			Thread.sleep(10);
		}
	}
}