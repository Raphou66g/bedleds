// #include "Wire.h"
// #include "LiquidCrystal_I2C.h"
#include <FastLED.h>
#include "SoftwareSerial.h"

//PINS
#define TX 2
#define RX 3
#define LED_PIN 11
#define STATE_PIN 5
#define RED 10
#define BLUE 12

#define NUM_LEDS 210
#define Z0 0
#define Z1 60
#define Z2 120
#define Z3 180

String* arrays[4];

CRGBArray<NUM_LEDS> leds;

// LiquidCrystal_I2C lcd(0x27, 16, 2);  // Addr, L, h
boolean back = true;
unsigned long timer_back;
unsigned long timer_duration;

SoftwareSerial BTSerial(TX, RX);

char serialString[100];
char myString[100];

// String menu_main[] = { "All lights", "Zone 1", "Zone 2", "Zone 3", "Zone 4" };
// String menu_secondary[] = { "Color", "Brightness" };
// String menu_colors[] = { "Red", "Green", "Blue", "Yellow", "Purple", "White", "Black" };
// String menu_brightness[] = { "0", "10", "20", "30", "40", "50", "60", "70", "80", "90", "100" };

// int val_brightness[] = { 0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100 };
// int menu_len[] = { 5, 2, 7, 11 };

int menu_state = 0;
int menu_line = 0;
int zone = -1;

boolean inputed = false;

CHSV color_zones[] = { CHSV(0, 0, 0), CHSV(0, 0, 0), CHSV(0, 0, 0), CHSV(0, 0, 0) };

void displayLeds() {
  leds(Z0, Z1 - 1) = color_zones[0];
  FastLED.show();
  leds(Z1, Z2 - 1) = color_zones[1];
  FastLED.show();
  leds(Z2, Z3 - 1) = color_zones[2];
  FastLED.show();
  leds(Z3, NUM_LEDS - 1) = color_zones[3];
  FastLED.show();
}

void setZone(int space, CHSV chsv) {
  if (space == 0) {
    for (int i = 0; i < 4; i++) {
      color_zones[i] = chsv;
    }
  } else {
    color_zones[space - 1] = chsv;
  }
  displayLeds();
}

void colorPicker(int line) {
  switch (line) {
    case 0:
      setZone(zone, CHSV(HUE_RED, 255, 255));
      break;
    case 1:
      setZone(zone, CHSV(HUE_GREEN, 255, 255));
      break;
    case 2:
      setZone(zone, CHSV(HUE_BLUE, 255, 255));
      break;
    case 3:
      setZone(zone, CHSV(HUE_YELLOW, 255, 255));
      break;
    case 4:
      setZone(zone, CHSV(HUE_PURPLE, 255, 255));
      break;
    case 5:
      setZone(zone, CHSV(0, 0, 255));
      break;
    default:
      setZone(zone, CHSV(0, 0, 0));
  }
}

void brightnessPicker(int menu_line) {
  CHSV chsv = color_zones[zone - 1];
  // int val = 255 * val_brightness[menu_line] / 100;
  // chsv.val = val;
  setZone(zone, chsv);
}

String retreiveColor() {
  int space = atoi(myString[3]);
  String str = "";
  if (space == 0) {
    for (int i = 0; i < 4; i++) {
      str = str + "@" + (i + 1) + "#" + color_zones[i].h + "#" + color_zones[i].s + "#" + color_zones[i].v;
    }
  } else {
    str = str + "@" + space + "#" + color_zones[space - 1].h + "#" + color_zones[space - 1].s + "#" + color_zones[space - 1].v;
  }
  Serial.print("\n");
  Serial.print(str);
  return str;
}

int getInt(int index) {
  int result = 0;
  int currentNumber = -1;
  boolean digitFound = false;

  for (int i = 0; i < sizeof(myString); i++) {
    if (myString[i] >= '0' && myString[i] <= '9') {
      result = result * 10 + (myString[i] - '0');
      digitFound = true;
    } else {
      if (digitFound) {
        currentNumber++;
        if (currentNumber == index) {
          return result;
        }
        result = 0;
        digitFound = false;
      }
    }
  }
  if (digitFound) {
    currentNumber++;
    if (currentNumber == index) {
      return result;
    }
  }
  return -1;
}

void applyColor() {
  Serial.print("Apply : ");
  for (int i = 0; i < 4; i++) {
    int mod = i * 4;
    int space = getInt(1 + mod);
    Serial.print("@");
    Serial.print(space);
    if (space < 0 || space > 4) {
      break;
    }
    int h = getInt(2 + mod);
    int s = getInt(3 + mod);
    int v = getInt(4 + mod);
    Serial.print(" h");
    Serial.print(h);
    Serial.print(" s");
    Serial.print(s);
    Serial.print(" v");
    Serial.println(v);
    setZone(space, CHSV(h, s, v));
    if (space == 0) {
      break;
    }
  }
  BTSerial.println("!!Applied");
}

void blink(int pin) {
  digitalWrite(pin, HIGH);
  delay(200);
  digitalWrite(pin, LOW);
}

void setup() {
  // put your setup code here, to run once:
  Serial.begin(9600);
  BTSerial.begin(9600);

  pinMode(STATE_PIN, INPUT);
  pinMode(RED, OUTPUT);
  pinMode(BLUE, OUTPUT);

  FastLED.addLeds<NEOPIXEL, LED_PIN>(leds, NUM_LEDS);
  leds(0, NUM_LEDS - 1) = color_zones[0];
  FastLED.show();

  timer_back = millis();

  memset(serialString, '\0', sizeof(serialString));
  BTSerial.flush();
  Serial.println("Init done");
}

long last = millis();

void loop() {

  long now = millis();

  if (now - last >= 3000) {

    Serial.println(digitalRead(STATE_PIN));

    if (digitalRead(STATE_PIN) == HIGH) {
      blink(BLUE);
    } else {
      blink(RED);
    }
    last = now;
  }

  if (BTSerial.available() > 0) {
    BTSerial.readBytes(serialString, sizeof(serialString));
    strcpy(myString, serialString);
    Serial.print("BT : ");
    Serial.print(myString);
    if (myString[0] == '!') {
      applyColor();
      // }
    } else if (myString[0] == '?') {
      String msg = retreiveColor();
      BTSerial.println(msg);
    }
    BTSerial.flush();
    memset(serialString, '\0', sizeof(serialString));
    memset(myString, '\0', sizeof(myString));
  }
}
