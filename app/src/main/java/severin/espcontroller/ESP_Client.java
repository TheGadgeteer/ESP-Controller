package severin.espcontroller;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.io.IOException;
import java.util.Observable;

/**
 * Created by Severin on 13.12.2017.
 *
 * Notifies Observers when Connection starts/ends.
 */

public class ESP_Client extends Observable implements Closeable{
    private final static String VERSION = "v1.0";
    private final static int minSpeedVal = 110;

    public final static String defaultIPAddress = "192.168.43.227";
    public final static int defaultPort = 1337;
    public static final int maxSpeed = 1023;

    private Socket socket;
    private String IPAddress =  defaultIPAddress;
    private int port = defaultPort;

    private Direction leftDir = Direction.NONE, rightDir = Direction.NONE;
    private int leftSpeed = -1, rightSpeed = -1;

    private static ESP_Client unique;
    private Thread timeoutThread = null;

    public static ESP_Client instance() {
        if (unique == null)
            unique = new ESP_Client();
        return unique;
    }

    private ESP_Client() {
    }

    /**
     *
     * @return: 0 - successful, 1 - server is busy, 2 - wrong version, -1 - server not available
     */
    public int startConnection() {
        if (isConnected())
            endConnection();

        boolean serverApproved = false;
        int retVal = -1; //Server not Available
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(IPAddress, port), 5000);
            retVal = 1;  //Server is busy
            socket.setTcpNoDelay(true);
            String toSend = "ESP_CLIENT " + VERSION + "\n";
            socket.getOutputStream().write(toSend.getBytes());
            for (int i = 0; i < 200; ++i){
                if (socket.getInputStream().available() == 0)
                    try {Thread.sleep(5); } catch(InterruptedException e){}
                else {
                        StringBuilder s = new StringBuilder();
                        char c;
                        do {
                            c = (char)socket.getInputStream().read();
                            s.append(c);
                        } while (c != '\n' && c!= -1);
                        if (s.toString().compareTo("ESP_SERVER " + VERSION + "\n") == 0)
                            serverApproved = true;
                        else
                            retVal = 2; //Wrong Version
                        break;
                    }
            }
        } catch (IOException e) {
        } finally {
            if (!serverApproved) {
                endConnection();
            } else {
                retVal = 0;
                timeoutThread = new Thread(new TimeoutRunnable());
                timeoutThread.start();
            }
            this.setChanged();
            this.notifyObservers();
        }
        return retVal;
    }

    public void endConnection() {
        endConnection(null);
    }

    public void endConnection(Object notifyObject) {
        if (isConnected() && !(notifyObject instanceof ConnectionLost))
            setEngines(false);
        try {
            if (socket != null)
                socket.close();
            socket = null;
        } catch (IOException e) {
        } finally {
            this.setChanged();
            this.notifyObservers(notifyObject);
            //Interrupt the Timeout Thread so it will end itself
            if (timeoutThread != null) {
                timeoutThread.interrupt();
                timeoutThread = null;
            }
        }
    }

    @Override
    public void close() {
        endConnection();
    }

    public void setEngines(boolean ON) {
        if (ON) {
            if (leftDir == Direction.NONE)
                setDirection(Direction.FORWARD, Motor.LEFT);
            if (rightDir == Direction.NONE)
                setDirection(Direction.FORWARD, Motor.RIGHT);
        } else {
            setDirection(Direction.NONE, Motor.LEFT);
            setDirection(Direction.NONE, Motor.RIGHT);
        }
    }


    //returns 0 when connection is success
    public void setDirection(Direction d, Motor m) {
        if (!isConnected())
            return;

        byte[] b = new byte [4];

        if (m == Motor.LEFT) {
            if (leftDir == d)
                return;
            b[0] = 'l';
            leftDir = d;
        } else { //m must be Motor.RIGHT
            if (rightDir == d)
                return;
            b[0] = 'r';
            rightDir = d;
        }

        b[1] = 'd';

        if (d == Direction.FORWARD) {
            b[2] = 'f';
        } else if (d == Direction.BACKWARD){
            b[2] = 'b';
        } else { //turn engine off
            b[2] = 'n';
        }

        b[3] = '\n';

        send(b);
    }

    public void setSpeed(int speed, Motor m) {
        if (!isConnected() || (leftDir == Direction.NONE && rightDir == Direction.NONE))
            return;
        speed = Math.min(Math.max(speed, -maxSpeed), maxSpeed); //Cap speed -maxSpeed and maxSpeed
        if (Math.abs(speed) <= minSpeedVal)
            speed = 0;

        byte[] b =  new byte[5];

        if (m == Motor.LEFT) {
            if (speed == leftSpeed)
                return;
            leftSpeed = speed;
            b[0] = 'l';
            if (speed > 0 && leftDir != Direction.FORWARD)
                setDirection(Direction.FORWARD, Motor.LEFT);
            else if (speed < 0 && leftDir != Direction.BACKWARD)
                setDirection(Direction.BACKWARD, Motor.LEFT);

        } else { //m must be Motor.RIGHT
            if (speed == rightSpeed)
                return;
            rightSpeed = speed;
            b[0] = 'r';
            if (speed > 0 && rightDir != Direction.FORWARD)
                setDirection(Direction.FORWARD, Motor.RIGHT);
            else if (speed < 0 && rightDir != Direction.BACKWARD)
                setDirection(Direction.BACKWARD, Motor.RIGHT);
        }
        speed = Math.abs(speed);
        b[1] = 'v';
        b[2] = (byte)speed;
        b[3] = (byte)(speed >> 8);
        b[4] = '\n';

        send(b);
    }

    //synchronized: Only one thread can send at once.
    private synchronized void send(byte[] b) {
        try {
            socket.getOutputStream().write(b);
        } catch(IOException e) {
            endConnection(new ConnectionLost());
        }
    }

    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }


    public void setIPAddress(String IP) {
        if (!IP.matches("\\d+\\.\\d+\\.\\d+\\.\\d+"))
            return;
        this.IPAddress = IP;
        endConnection();
        setChanged();
        notifyObservers();
    }

    public void setPort(int port) {
        if (port < 0 || port > 1 << 16)
            return;
        this.port = port;
        endConnection();
        setChanged();
        notifyObservers();
    }

    public int getPort() {
        return port;
    }

    public String getIPAddress() {
        return IPAddress;
    }

    public enum Motor {LEFT, RIGHT}
    public enum Direction {FORWARD, BACKWARD, NONE}

    public class ConnectionLost {
        private ConnectionLost(){}
    }


    private class TimeoutRunnable implements Runnable {
        private static final int sleepTime = 1000;
        private static final int timeoutTime = 4000;
        private final byte[] ACK = {6};
        @Override
        public void run() {
            int msgTimeAgo = 0;
            try {
                while ( socket!= null && socket.isConnected()) {
                    send(ACK); //Send own signal so NodeMCU can make sure the phone didn't time out
                    if (socket.getInputStream().available() == 0) { //Test if NodeMCU sent a message to confirm it didn't time out
                        msgTimeAgo += sleepTime;
                        if (msgTimeAgo >= timeoutTime) {
                            new Thread() {
                                @Override
                                public void run() {
                                    endConnection(new ConnectionLost());
                                }
                            }.start();
                            return;
                        }
                    } else {
                        msgTimeAgo = 0;
                        socket.getInputStream().read();
                    }
                    Thread.sleep(sleepTime);
                }
            } catch (InterruptedException | IOException e) {
                return;
            }
        }
    }
}
