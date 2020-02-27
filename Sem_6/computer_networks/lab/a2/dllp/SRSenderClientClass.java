package dllp;
/**Data format:
 * 1 byte: Info
 * 1 byte: Sequence number
 * 1 - 7 bytes: data
 *
 * Info byte structrue:
 * 00000000: message
 * 10000000: ack 
 * 11000000: nak
 * 11111111: invalid frame
 *
 * */
class SRSenderClientClass extends ClientClass {
	/**Send and Wait data link layer protocol implementation */
	
	protected static final String SENDER_MAC_ADDR 		= "10101010";
	protected static final String RECEIVER_MAC_ADDR 	= "10101011";
	protected static final String MESSAGE_HEADER 		= "00000000";
	protected static final String AWK_HEADER 		= "10000000";
	protected static final String NAK_HEADER 		= "11000000";
	protected static final String INVALID_FRAME 		= "11111111";

	protected String MSG;
	protected int EVENT_REQ_TO_SEND;
	protected boolean EVENT_TIMEOUT;
	protected Timer timer;
	protected String frames[];
	protected Timer timers[];
	protected int sw = 4;
	protected int sf = 0;
	protected int sn = 0;
	
//--------------------------------------

	public class Timer extends Thread{
		private boolean running;
		private final static int TIME = 100000;
		private int time;
		int frame;
		
		public Timer(int f) {
			running = false;
			time = TIME;
			frame = f;
		}

		public boolean isRunning() {return running;}

		public  void startTimer() {
			running = true;
			EVENT_TIMEOUT = false;
			time = TIME;
			resume();
		}
	
		public  void stopTimer() {
			try {
				time = TIME;
				running = false;
				suspend();
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(0);
			}
		}

		public void run() {
			try {
				while(true) {
					while((time--)!=0) {
						sleep(1);
					}
					EVENT_TIMEOUT = true;
					System.out.println("Timer timeout");
					timeout();
				}
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(0);
			}
		}
	}
//--------------------------------------


	public SRSenderClientClass() {
		super();
		MSG = "000111";	
		EVENT_REQ_TO_SEND = 0;
		EVENT_TIMEOUT = false;
	}

	protected class FrameSendingRequester implements Runnable {
		public void run() {
			int iter = 10;
			while((iter--)!=0) {
				requestToSend();
			}
		}

		public synchronized void requestToSend() {
			EVENT_REQ_TO_SEND++;
		}
	}
	
	protected void printN(Integer ack) {
		if(ack!=null)
			System.out.println("sn:" + sn
				+ " sf:" + sf
				+ " sw:" + sw
				+ " ack:" + ack
				);
		else 
			
			System.out.println("sn:" + sn
				+ " sf:" + sf
				+ " sw:" + sw
				);
	}

	protected String makeSequenceString (int n) {
		String sq = Integer.toBinaryString(n);
		if(sq.length() > 8)
			sq = sq.substring(sq.length() - 8);
		else if(sq.length() < 8)
			while(sq.length()!=8)
				sq = "0" + sq;
		return sq;
	}

	protected void sendFrame(int sn) {
		System.out.println("Sending Message-"
				+ sn
				+ ": " 
				+ MSG 
				+ " to:"
				+ RECEIVER_MAC_ADDR);
		frames[sn] = MESSAGE_HEADER + makeSequenceString(sn) + MSG;
		sendMsg(frames[sn], RECEIVER_MAC_ADDR);
		EVENT_REQ_TO_SEND--;
	}

	public void run() {
		super.run(SENDER_MAC_ADDR);

		sw = 3;
		sf = 0;
		sn = 0;

		frames = new String[sw + 1];
		timers = new Timer[sw +  1];
		for(int i=0;i<sw + 1;i++) {
			frames[i] = MSG;	
			timers[i].start();
		}

		new Thread(new FrameSendingRequester()).start();

		while(true) {
			try {
				if(EVENT_REQ_TO_SEND!=0) {
					int nf = sn - sf;
					if(nf<0) nf = nf + sw + 1;
					if(nf >= sw){}
					else {
						sendFrame(sn);
						sn = (sn + 1)%(sw + 1);
						Thread.sleep(3000);
						timers[sn].startTimer();
					}
				}

				/*if(EVENT_TIMEOUT) {
					wait();
				}*/

			} catch (Exception e) {
				e.printStackTrace();
				System.exit(0);
			}
		}
	}

	protected boolean checkValidAck(int ackNo) {
		int f=-1,n=-1,w=-1,shift=-1;
		if(sf<sn)
			if(ackNo>sf && ackNo<=sn)
				return true;
		if(sf==sn)
			return true;
		if(sf>sn) {
			f = sf;
			n = sn;
			w = sw * 2;
			shift = w - f;
			f = (f+shift)%w;
			n = (n+shift)%w;
			ackNo = (ackNo + shift)%w;
			if(ackNo>f && ackNo<=n)
				return true;
		}
		printN(ackNo);
		System.out.print(f + " " + n + " " + ackNo + " ");
		System.out.print(ackNo>sf);
		System.out.println(ackNo<=sn);

		return false;
	}

	@Override
	protected void receiveMsg(String msg) {
		String dest_mac = msg.substring(0,8);
		String source_mac = msg.substring(8,16);
		String info = msg.substring(16,24);
		if(info.equals(AWK_HEADER)) {
			int ackNO = Integer.parseInt(msg.substring(24,32),2);
			//printN(ackNO);
			if(checkValidAck(ackNO)) { //problem here
				while(sf != ackNO){
					System.out.println("Ack-" 
						+ ackNO 
						+ " received from:" 
						+ source_mac);
					//printN(ackNO);
					//frame[sf] = INVALID_FRAME;
					timers[sf].stopTimer();
					sf = (sf + 1) % (sw * 2);
				} 
				//printN(null);
				timer.stopTimer();
			}	
		} else if(info.equals(NAK_HEADER)) {
			int nakNO = Integer.parseInt(msg.substring(24,32),2);
			if(checkValidAck(nakNO)) {
				while(sf != nakNO) {
					System.out.println("Ack-" 
						+ nakNO 
						+ " received from:" 
						+ source_mac);
					System.out.println("Resending-" + sf);
					sendFrame(sf);
					timers[sf].startTimer();
				}
			}
		}
	}
	
	public void timeout() {
		timer.startTimer();
		System.out.println("Trying to resend");
		System.out.println("sn:" + sn
			+ " sf:" + sf
			+ " sw:" + sw
			);
		int temp = sf - sn; //problem here
		if(temp <0) temp = sw + 1 + temp;
		int i = 0;
		while(i < temp) {
			System.out.println("Resending-" + temp);
			sendFrame((sn + temp)%(sw * 2));
			i++;
		}
		//notifyAll();
	}
}
