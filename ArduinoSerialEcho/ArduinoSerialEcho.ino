// Arduino program to echo all data received to the serial port.
// Hook up a bluefruit to the hardware serial port and use the
// android Bluetooth Test program to test sending and receiving data.
#define BUFFER_SIZE 1024

void setup() {
  Serial.begin(115200);
  Serial.setTimeout(100);
}

void loop() {
  if (Serial.available() > 0) {
    char buffer[BUFFER_SIZE];
    memset(buffer, 0, BUFFER_SIZE);
    int len = Serial.readBytes(buffer, BUFFER_SIZE-1);
    Serial.print(buffer);
  }
  delay(100);
}
