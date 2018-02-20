/*
 * Connects to Smartphone Hotspot per Wifi, then sets up a server the app ESPController can connect to.
 * The ESPController then sends signals which the Program interprets and adjusts the motor speed and direction to it.
 * 
 * LED D0 (Lower LED):  Is On when the server is waiting for a new connection
 * LED D4 (Upper LED): Is On when the server sets up the Wifi Connection and the Server and when the engine is on
 * 
 * signal language used:
 *      [l/r]v<short>\n //adjust speed of left/right motor: as short between 0 and 1023
 *      [l/r]d<f/b/n>\n //direction of left/right motor: f : forward, b : backward, n : turn motor off
 */
#include <ESP8266WiFi.h>

//#define MOTOR_LIGHT     //Uncomment this line if the D4 LED should be on when the engine is turned on
//#define DEBUG         //Comment this line if the server should not print any debug messages
//#define ACCESSPOINT   //Uncomment this line if the NodeMCU should make a Soft AccessPoint instead of Connecting to a Wifi Network.

#define VERSION "v1.0"
#define ACK 6 //acknowledge byte, sent every second so the client knows the server didnt time out

#define SERVER_PORT 1337

#define PIN_LED D0 //Lower LED, on when LOW (default:LOW)
#define PIN_LED2 D4 //Upper LED, on when LOW(default:LOW)

//index of the designated pins in the lMotor, rMotor arrays
#define IDX_PIN_FOR 0
#define IDX_PIN_BACK 1
#define IDX_PIN_PWM 2


void flushClient();
void findClient();

//arrays to save the pins for each motor at the idx
int lMotor[] = {D6, D5, D7};
int rMotor[] = {D3, D2, D1};

WiFiServer server(SERVER_PORT);
WiFiClient client;
int loopsSinceACK = 0;

void setup() {
  //Light LED when starting setup, turn it off when closing the setup
  pinMode(PIN_LED, OUTPUT);
  pinMode(PIN_LED2, OUTPUT);

  //set digital Pins as output pins and set values
  for (int i = 0; i < 2; ++i) {
    pinMode(lMotor[i], OUTPUT);
    pinMode(rMotor[i], OUTPUT);
  }
  digitalWrite(PIN_LED, HIGH);
  digitalWrite(PIN_LED2, LOW);
    
  delay(10);
  
  #ifdef DEBUG
    Serial.begin(57600);
  #endif

  #ifdef ACCESSPOINT
      IPAddress local_IP(192, 168, 0, 1);
      IPAddress gateway(192, 168, 9, 4);
      IPAddress subnet(255, 255, 255, 0);

      const char *ssid = "ESP Gruppe 4";       
      const char *password = "gruppe04";
      
      #ifdef DEBUG
        Serial.print("Setting soft-AP configuration ... ");
      #endif
      bool ret = WiFi.softAPConfig(local_IP, gateway, subnet);
      #ifdef DEBUG
        Serial.println(ret ? "Ready" : "Failed!");
        Serial.print("Setting soft-AP.. ");
      #endif
      delay(20);
      ret = WiFi.softAP(ssid, password, 1, false);
    
      #ifdef DEBUG
        Serial.println(ret ? "Ready" : "Failed!");
        Serial.print("Soft-AP IP address = ");
        Serial.println(WiFi.softAPIP());
      #endif
  #else
        const char *ssid = "Severin Hotspot";
        const char *password = "gruppe04";
        
        #ifdef DEBUG
            Serial.print("Connecting to ");
            Serial.print(ssid);
        #endif
        
        WiFi.begin(ssid, password);
        delay(20);
        while (WiFi.status() != WL_CONNECTED) {
            delay(200);
            #ifdef DEBUG
                Serial.print(".");
            #endif
        }
        #ifdef DEBUG
            Serial.println("\nWiFi connected");
        #endif
        #ifdef DEBUG
            Serial.print("IP address: ");
            Serial.println(WiFi.localIP());
        #endif
    #endif
    delay(20);
    // Start the server
    server.setNoDelay(true);
    server.begin();
  
    #ifdef DEBUG
        Serial.println("Server started");
    #endif

    digitalWrite(PIN_LED2, HIGH);
    delay(20);
    findClient();
}


void loop() {
    loopsSinceACK++;
    //the program allows only one Client to be connected at the same time
    if (!client.connected()) { //get new main Client
        #ifdef DEBUG
            Serial.println("Client disconnected.");
        #endif
        findClient();
        loopsSinceACK = 0;
    }
    WiFiClient::stopAllExcept(&client);
    delay(2);

    client.write(ACK);
      #ifdef DEBUG
       Serial.printf("Loops since last ACK from Client: %d\n", loopsSinceACK);
      #endif

    if (loopsSinceACK > 2) { //client timed out
        #ifdef DEBUG
            Serial.println("Client timed out.");
        #endif
        client.stop();
        client = WiFiClient();
        return;
    }
    
    for (int i = 0; i < 500; ++i) {    
        if (client.available()) {           
            char motor = client.read(); //first char says left or right motor
            //Test if it was just an ACKnowledge byte sent to confirm the connection didnt timeout
            if (motor == ACK) {
                loopsSinceACK = 0;
                continue;
            }
            char mode = client.read();  //second char says if you should adjust the speed or turn the motor on/off

            #ifdef DEBUG
                Serial.printf("Recv %c%c\n", motor, mode);
            #endif
            int *pins;
            if (motor == 'l') {
                pins = lMotor;
            } else if (motor == 'r') {
                pins = rMotor;
            } else { //received data in wrong format
                flushClient();
                continue;
            }
                         
            if (mode == 'v') {
                int speed = client.read();
                speed |= client.read() << 8;
                analogWrite(pins[IDX_PIN_PWM], speed);
                #ifdef DEBUG
                    Serial.printf("Set analog pin %d to %d\n", pins[IDX_PIN_PWM], speed);
                #endif
            } else if (mode == 'd') {
                char c = client.read();
                if (c == 'f') {
                    digitalWrite(pins[IDX_PIN_BACK], LOW);
                    digitalWrite(pins[IDX_PIN_FOR], HIGH);
                    
                    #ifdef MOTOR_LIGHT
                        digitalWrite(PIN_LED2, LOW);
                    #endif
                } else if (c == 'b') {
                    digitalWrite(pins[IDX_PIN_FOR], LOW);
                    digitalWrite(pins[IDX_PIN_BACK], HIGH);

                    #ifdef MOTOR_LIGHT
                        digitalWrite(PIN_LED2, LOW);
                    #endif
                } else if (c == 'n') {
                    analogWrite(pins[IDX_PIN_PWM], 0);
                    digitalWrite(pins[IDX_PIN_FOR], LOW);
                    digitalWrite(pins[IDX_PIN_BACK], LOW);
                    
                    #ifdef MOTOR_LIGHT
                        digitalWrite(PIN_LED2, HIGH);
                    #endif
                }
            }
            delay(2);
            flushClient(); 
        }
        delay(2);
    }            
    
}


void findClient() {
    digitalWrite(PIN_LED, LOW);

    //set all pins off
    digitalWrite(PIN_LED2, HIGH);
    digitalWrite(lMotor[IDX_PIN_FOR], LOW);
    digitalWrite(lMotor[IDX_PIN_BACK], LOW);
    analogWrite(lMotor[IDX_PIN_PWM], 0);
    digitalWrite(rMotor[IDX_PIN_FOR], LOW);
    digitalWrite(rMotor[IDX_PIN_BACK], LOW);
    analogWrite(rMotor[IDX_PIN_PWM], 0);
    
    bool clientApproved = false;
    #ifdef DEBUG
        Serial.println("Finding new main Client..");
    #endif
    do {
        client = server.available();
        if (client) {
            for (int i = 0; i < 200; ++i) { //Wait 1 Second for Message By client
                delay(5);
                if (client.available()) {
                    String response = client.readStringUntil('\n');
                    Serial.println(response);
                    if (response.equals(String("ESP_CLIENT ") + VERSION)) {
                        clientApproved = true;
                    }
                    break;
                }
            }
            if (!clientApproved) {
                    client.printf("Wrong Client, needs ESP_CLIENT %s\n", VERSION);
                    client.stop();
            }
        }
        delay(100);
    } while (!clientApproved);

    client.printf("ESP_SERVER %s\n", VERSION);
    
    #ifdef DEBUG
        Serial.print("Connected to Client at ");
        Serial.println(client.remoteIP());
    #endif

    digitalWrite(PIN_LED, HIGH);
    delay(20);
}


void flushClient() {
    char c;
    do {
        c = client.read();
    } while (c != '\n' && c != -1);
}
