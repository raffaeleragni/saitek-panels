import ch.ntb.usb.Device;
import ch.ntb.usb.USB;
import ch.ntb.usb.USBTimeoutException;
import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.util.Arrays;

public class Main
{
    private static final int VENDOR_SAITEK = 0x6a3;
    private static final int DEVICE_SWITCH = 0xd67;
    private static final int DEVICE_RADIO = 0xd05;
    private static final boolean DEBUG_FLAGS = false;
    private static final boolean DEBUG_SWITCHES = false;
    private static final boolean DEBUG_RADIO = false;
    
    private static final boolean DEBUG_SWITCHES_DISABLE_SHORTCUTS = false;
    private static final boolean DEBUG_RADIO_DISABLE_SHORTCUTS = false;
    
    public static void main(String[] args) throws Exception
    {
        System.out.println("CLOSING THIS WINDOW WILL CLOSE THE PROGRAM");
        System.out.println("starting...");
        switchReader.start();
        radioReader.start();
    }

    static String toBIN(byte[] b)
    {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++)
        {
            String s = Integer.toBinaryString(b[i] & 0xff);
            for (int j = 8; j > s.length(); j--)
                sb.append("0");
            sb.append(Integer.toBinaryString(b[i] & 0xff)).append(" ");
        }
        return sb.toString();
    }

    static boolean flagChanged(byte oldV, byte newV, int mask)
    {
        return ((oldV ^ newV) & mask) > 0;
    }

    static boolean flagValue(byte v, int mask)
    {
        return (v & mask) > 0;
    }
    
    // -------------------------------------------------------------------------
    // SWITCH PART
    
    // Group 1
    public static final int SWITCHKEY_MASTER_BAT = 0b1;
    public static final int SWITCHKEY_MASTER_ALT = 0b10;
    public static final int SWITCHKEY_AVIONICS_MASTER = 0b100;
    public static final int SWITCHKEY_FUEL_PUMP = 0b1000;
    public static final int SWITCHKEY_DE_ICE = 0b10000;
    public static final int SWITCHKEY_PITOT_HEAT = 0b100000;
    public static final int SWITCHKEY_CLOSE_COWL = 0b1000000;
    public static final int SWITCHKEY_LIGHTS_PANEL = 0b10000000;
    // Group 2
    public static final int SWITCHKEY_LIGHTS_BEACON = 0b1;
    public static final int SWITCHKEY_LIGHTS_NAV = 0b10;
    public static final int SWITCHKEY_LIGHTS_STROBE = 0b100;
    public static final int SWITCHKEY_LIGHTS_TAXI = 0b1000;
    public static final int SWITCHKEY_LIGHTS_LANDING = 0b10000;
    public static final int SWITCHKEY_ENGINE_OFF = 0b100000;
    public static final int SWITCHKEY_ENGINE_RIGHT = 0b1000000;
    public static final int SWITCHKEY_ENGINE_LEFT = 0b10000000;
    // Group 3
    public static final int SWITCHKEY_ENGINE_BOTH = 0b1;
    public static final int SWITCHKEY_ENGINE_START = 0b10;
    public static final int SWITCHKEY_GEAR_UP = 0b100;
    public static final int SWITCHKEY_GEAR_DOWN = 0b1000;

    interface SwitchCallback
    {
        void changed(boolean value);
    }
    
    public static void dataChangedSwitches(byte[] oldData, byte[] newData)
    {
        if (oldData == null || newData == null || oldData.length != newData.length || Arrays.equals(oldData, newData))
            return;

        checkSwitch(oldData[0], newData[0], SWITCHKEY_MASTER_BAT, "MASTER BAT", masterARM);
        checkSwitch(oldData[0], newData[0], SWITCHKEY_MASTER_ALT, "MASTER ALT", masterLASE);
        checkSwitch(oldData[0], newData[0], SWITCHKEY_AVIONICS_MASTER, "AVIONICS MASTER", gunARM);
        checkSwitch(oldData[0], newData[0], SWITCHKEY_FUEL_PUMP, "FUEL PUMP", toggleTGP);
        checkSwitch(oldData[0], newData[0], SWITCHKEY_DE_ICE, "DE-ICE", null);
        checkSwitch(oldData[0], newData[0], SWITCHKEY_PITOT_HEAT, "PITOT HEAT", null);
        checkSwitch(oldData[0], newData[0], SWITCHKEY_CLOSE_COWL, "CLOSE/COWL", null, "CLOSE", "COWL");
        checkSwitch(oldData[0], newData[0], SWITCHKEY_LIGHTS_PANEL, "LIGHTS PANEL", null);

        checkSwitch(oldData[1], newData[1], SWITCHKEY_LIGHTS_BEACON, "LIGHTS BEACON", null);
        checkSwitch(oldData[1], newData[1], SWITCHKEY_LIGHTS_NAV, "LIGHTS NAV", lightsSwitch);
        checkSwitch(oldData[1], newData[1], SWITCHKEY_LIGHTS_STROBE, "LIGHTS STROBE", anticollisionLights);
        checkSwitch(oldData[1], newData[1], SWITCHKEY_LIGHTS_TAXI, "LIGHTS TAXI", taxiLights);
        checkSwitch(oldData[1], newData[1], SWITCHKEY_LIGHTS_LANDING, "LIGHTS LANDING", landingLights);
        checkSwitch(oldData[1], newData[1], SWITCHKEY_ENGINE_OFF, "ENGINE OFF", boatCENTER);
        checkSwitch(oldData[1], newData[1], SWITCHKEY_ENGINE_RIGHT, "ENGINE RIGHT", boatAFT);
        checkSwitch(oldData[1], newData[1], SWITCHKEY_ENGINE_LEFT, "ENGINE LEFT", boatFWD);

        checkSwitch(oldData[2], newData[2], SWITCHKEY_ENGINE_BOTH, "ENGINE BOTH", null);
        checkSwitch(oldData[2], newData[2], SWITCHKEY_ENGINE_START, "ENGINE START", null);
        checkSwitch(oldData[2], newData[2], SWITCHKEY_GEAR_UP, "GEAR UP", gearUp);
        checkSwitch(oldData[2], newData[2], SWITCHKEY_GEAR_DOWN, "GEAR DOWN", gearDown);
    }

    static void checkSwitch(byte oldV, byte newV, int mask, String description, final SwitchCallback callback, String... values)
    {
        if (oldV != newV && flagChanged(oldV, newV, mask))
        {
            final boolean value = flagValue(newV, mask);
            if (DEBUG_SWITCHES)
            {
                System.out.println(description + ": " + (value ? (values.length > 0 ? values[0] : "ON") : (values.length > 1 ? values[1] : "OFF")));
            }
            if (callback != null && !DEBUG_SWITCHES_DISABLE_SHORTCUTS)
            {
                java.awt.EventQueue.invokeLater(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        callback.changed(value);
                    }
                });
            }
        }
    }
    
    static Thread switchReader = new Thread(new Runnable()
    {
        @Override
        public void run()
        {
            try
            {
                System.out.println("starting switch panel reader");
                boolean firstRun = true;
                Device dev = USB.getDevice((short) VENDOR_SAITEK, (short) DEVICE_SWITCH);
                try
                {
                    dev.open(1, 0, -1);
                    //Initialize it to: all OFF, gear DOWN & engine OFF
                    byte[] data = new byte[]
                    {
                        0b00000000, 0b00100000, 0b00001000, 0b00000000
                    };
                    byte[] oldData = new byte[]
                    {
                        0b00000000, 0b00100000, 0b00001000, 0b00000000
                    };
                    while (true)
                    {
                        try
                        {
                            dev.readInterrupt(0x81, data, data.length, -1, false);
                        }
                        catch (USBTimeoutException e)
                        {
                        }

                        if (!Arrays.equals(oldData, data) && !firstRun)
                        {
                            if (DEBUG_FLAGS)
                            {
                                System.out.println("------------------");
                                System.out.println("SWITCH OLD: " + toBIN(oldData));
                                System.out.println("SWITCH NEW: " + toBIN(data));
                                System.out.println("------------------");
                            }
                            dataChangedSwitches(oldData, data);
                            System.arraycopy(data, 0, oldData, 0, data.length);
                        }

                        firstRun = false;

                        Thread.sleep(10);
                    }
                }
                finally
                {
                    if (dev.isOpen())
                    {
                        dev.close();
                        dev.reset();
                    }
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    });
    
    // -------------------------------------------------------------------------
    // RADIO PART
    
    private static final int RADIOKNOB1_SMALL_RIGHT = 0b1;
    private static final int RADIOKNOB1_SMALL_LEFT = 0b10;
    private static final int RADIOKNOB1_BIG_RIGHT = 0b100;
    private static final int RADIOKNOB1_BIG_LEFT = 0b1000;
    private static final int RADIOKNOB2_SMALL_RIGHT = 0b10000;
    private static final int RADIOKNOB2_SMALL_LEFT = 0b100000;
    private static final int RADIOKNOB2_BIG_RIGHT = 0b1000000;
    private static final int RADIOKNOB2_BIG_LEFT = 0b10000000;
    
    public static void dataChangedRadio(byte[] oldData, byte[] newData)
    {
        if (oldData == null || newData == null || oldData.length != newData.length || Arrays.equals(oldData, newData))
        {
            return;
        }

        checkRadio(oldData[2], newData[2], RADIOKNOB1_SMALL_RIGHT, "RADIOKNOB1_SMALL_RIGHT", courseRight);
        checkRadio(oldData[2], newData[2], RADIOKNOB1_SMALL_LEFT, "RADIOKNOB1_SMALL_LEFT", courseLeft);
        checkRadio(oldData[2], newData[2], RADIOKNOB1_BIG_RIGHT, "RADIOKNOB1_BIG_RIGHT", headRight);
        checkRadio(oldData[2], newData[2], RADIOKNOB1_BIG_LEFT, "RADIOKNOB1_BIG_LEFT", headLeft);
        checkRadio(oldData[2], newData[2], RADIOKNOB2_SMALL_RIGHT, "RADIOKNOB2_SMALL_RIGHT", null);
        checkRadio(oldData[2], newData[2], RADIOKNOB2_SMALL_LEFT, "RADIOKNOB2_SMALL_LEFT", null);
        checkRadio(oldData[2], newData[2], RADIOKNOB2_BIG_RIGHT, "RADIOKNOB2_BIG_RIGHT", null);
        checkRadio(oldData[2], newData[2], RADIOKNOB2_BIG_LEFT, "RADIOKNOB2_BIG_LEFT", null);
    }

    static void checkRadio(byte oldV, byte newV, int mask, String description, final RadioCallback callback, String... values)
    {
        if (oldV != newV && flagChanged(oldV, newV, mask))
        {
            final boolean value = flagValue(newV, mask);
            if (DEBUG_RADIO)
            {
                System.out.println(description + ": " + (value ? (values.length > 0 ? values[0] : "ON") : (values.length > 1 ? values[1] : "OFF")));
            }
            if (callback != null && !DEBUG_RADIO_DISABLE_SHORTCUTS)
            {
                java.awt.EventQueue.invokeLater(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        callback.changed(value);
                    }
                });
            }
        }
    }

    interface RadioCallback
    {
        void changed(boolean value);
    }
    static Thread radioReader = new Thread(new Runnable()
    {
        @Override
        public void run()
        {
            try
            {
                System.out.println("starting radio panel reader");
                Device dev = USB.getDevice((short) VENDOR_SAITEK, (short) DEVICE_RADIO);
                try
                {
                    // BEWARE, all radio inputs are always doubled (WTF???)
                    dev.open(1, 0, -1);
                    byte[] data = new byte[3];
                    byte[] oldData = new byte[3];
                    while (true)
                    {
                        try
                        {
                            dev.readInterrupt(0x81, data, data.length, -1, false);
                        }
                        catch (USBTimeoutException e)
                        {
                        }

                        if (!Arrays.equals(oldData, data))
                        {
                            if (DEBUG_FLAGS)
                            {
                                System.out.println("------------------");
                                System.out.println("RADIO OLD: " + toBIN(oldData));
                                System.out.println("RADIO NEW: " + toBIN(data));
                                System.out.println("------------------");
                            }
                            dataChangedRadio(oldData, data);
                            System.arraycopy(data, 0, oldData, 0, data.length);
                        }

                        Thread.sleep(10);
                    }
                }
                finally
                {
                    if (dev.isOpen())
                    {
                        dev.close();
                        dev.reset();
                    }
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    });

    // SWITCHES KEY BINDINGS
    // <editor-fold defaultstate="collapsed" desc="boatAFT = ALT+1">
    static SwitchCallback boatAFT = new SwitchCallback()
    {
        @Override
        public void changed(boolean value)
        {
            if (value)
            {
                try
                {
                    Robot robot = new Robot();
                    robot.keyPress(KeyEvent.VK_ALT);
                    robot.keyPress(KeyEvent.VK_1);
                    Thread.sleep(300);
                    robot.keyRelease(KeyEvent.VK_1);
                    robot.keyRelease(KeyEvent.VK_ALT);
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
        }
    };// </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="boatCENTER = ALT+2">
    static SwitchCallback boatCENTER = new SwitchCallback()
    {
        @Override
        public void changed(boolean value)
        {
            if (value)
            {
                try
                {
                    Robot robot = new Robot();
                    robot.keyPress(KeyEvent.VK_ALT);
                    robot.keyPress(KeyEvent.VK_2);
                    Thread.sleep(300);
                    robot.keyRelease(KeyEvent.VK_2);
                    robot.keyRelease(KeyEvent.VK_ALT);
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
        }
    };// </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="boatFWD = ALT+3">
    static SwitchCallback boatFWD = new SwitchCallback()
    {
        @Override
        public void changed(boolean value)
        {
            if (value)
            {
                try
                {
                    Robot robot = new Robot();
                    robot.keyPress(KeyEvent.VK_ALT);
                    robot.keyPress(KeyEvent.VK_3);
                    Thread.sleep(300);
                    robot.keyRelease(KeyEvent.VK_3);
                    robot.keyRelease(KeyEvent.VK_ALT);
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
        }
    };// </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="gunARM = ALT+CTRL+P (ON), SHIFT+CTRL+P (OFF)">
    static SwitchCallback gunARM = new SwitchCallback()
    {
        @Override
        public void changed(boolean value)
        {
            if (value)
            {
                try
                {
                    Robot robot = new Robot();
                    robot.keyPress(KeyEvent.VK_ALT);
                    robot.keyPress(KeyEvent.VK_CONTROL);
                    robot.keyPress(KeyEvent.VK_P);
                    Thread.sleep(300);
                    robot.keyRelease(KeyEvent.VK_P);
                    robot.keyRelease(KeyEvent.VK_CONTROL);
                    robot.keyRelease(KeyEvent.VK_ALT);
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
            else
            {
                try
                {
                    Robot robot = new Robot();
                    robot.keyPress(KeyEvent.VK_SHIFT);
                    robot.keyPress(KeyEvent.VK_CONTROL);
                    robot.keyPress(KeyEvent.VK_P);
                    Thread.sleep(300);
                    robot.keyRelease(KeyEvent.VK_P);
                    robot.keyRelease(KeyEvent.VK_CONTROL);
                    robot.keyRelease(KeyEvent.VK_SHIFT);
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
        }
    };// </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="anticollisionLights = SHIFT+CTRL+A">
    static SwitchCallback anticollisionLights = new SwitchCallback()
    {
        @Override
        public void changed(boolean value)
        {
            try
            {
                Robot robot = new Robot();
                robot.keyPress(KeyEvent.VK_CONTROL);
                robot.keyPress(KeyEvent.VK_SHIFT);
                robot.keyPress(KeyEvent.VK_A);
                Thread.sleep(300);
                robot.keyRelease(KeyEvent.VK_A);
                robot.keyRelease(KeyEvent.VK_SHIFT);
                robot.keyRelease(KeyEvent.VK_CONTROL);
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
            }
        }
    };// </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="taxiLights = SHIFT+CTRL+T (ON), SHIFT+CTRL+O (OFF)">
    static SwitchCallback taxiLights = new SwitchCallback()
    {
        @Override
        public void changed(boolean value)
        {
            if (value)
            {
                try
                {
                    Robot robot = new Robot();
                    robot.keyPress(KeyEvent.VK_SHIFT);
                    robot.keyPress(KeyEvent.VK_CONTROL);
                    robot.keyPress(KeyEvent.VK_T);
                    Thread.sleep(300);
                    robot.keyRelease(KeyEvent.VK_T);
                    robot.keyRelease(KeyEvent.VK_CONTROL);
                    robot.keyRelease(KeyEvent.VK_SHIFT);
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
            else
            {
                try
                {
                    Robot robot = new Robot();
                    robot.keyPress(KeyEvent.VK_SHIFT);
                    robot.keyPress(KeyEvent.VK_CONTROL);
                    robot.keyPress(KeyEvent.VK_O);
                    Thread.sleep(300);
                    robot.keyRelease(KeyEvent.VK_O);
                    robot.keyRelease(KeyEvent.VK_CONTROL);
                    robot.keyRelease(KeyEvent.VK_SHIFT);
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
        }
    };// </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="landingLights = SHIFT+CTRL+L (ON), SHIFT+CTRL+O (OFF)">
    static SwitchCallback landingLights = new SwitchCallback()
    {
        @Override
        public void changed(boolean value)
        {
            if (value)
            {
                try
                {
                    Robot robot = new Robot();
                    robot.keyPress(KeyEvent.VK_SHIFT);
                    robot.keyPress(KeyEvent.VK_CONTROL);
                    robot.keyPress(KeyEvent.VK_L);
                    Thread.sleep(300);
                    robot.keyRelease(KeyEvent.VK_L);
                    robot.keyRelease(KeyEvent.VK_CONTROL);
                    robot.keyRelease(KeyEvent.VK_SHIFT);
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
            else
            {
                try
                {
                    Robot robot = new Robot();
                    robot.keyPress(KeyEvent.VK_SHIFT);
                    robot.keyPress(KeyEvent.VK_CONTROL);
                    robot.keyPress(KeyEvent.VK_O);
                    Thread.sleep(300);
                    robot.keyRelease(KeyEvent.VK_O);
                    robot.keyRelease(KeyEvent.VK_CONTROL);
                    robot.keyRelease(KeyEvent.VK_SHIFT);
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
        }
    };// </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="toggleTGP = ALT+T">
    static SwitchCallback toggleTGP = new SwitchCallback()
    {
        @Override
        public void changed(boolean value)
        {
            try
            {
                Robot robot = new Robot();
                robot.keyPress(KeyEvent.VK_ALT);
                robot.keyPress(KeyEvent.VK_T);
                Thread.sleep(300);
                robot.keyRelease(KeyEvent.VK_T);
                robot.keyRelease(KeyEvent.VK_ALT);
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
            }
        }
    };// </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="masterARM = ALT+M (ON), SHIFT+M (OFF)">
    static SwitchCallback masterARM = new SwitchCallback()
    {
        @Override
        public void changed(boolean value)
        {
            if (value)
            {
                try
                {
                    Robot robot = new Robot();
                    robot.keyPress(KeyEvent.VK_ALT);
                    robot.keyPress(KeyEvent.VK_M);
                    Thread.sleep(300);
                    robot.keyRelease(KeyEvent.VK_M);
                    robot.keyRelease(KeyEvent.VK_ALT);
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
            else
            {
                try
                {
                    Robot robot = new Robot();
                    robot.keyPress(KeyEvent.VK_SHIFT);
                    robot.keyPress(KeyEvent.VK_M);
                    Thread.sleep(300);
                    robot.keyRelease(KeyEvent.VK_M);
                    robot.keyRelease(KeyEvent.VK_SHIFT);
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
        }
    };// </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="masterLASE = CTRL+L (ON), SHIFT+L (OFF)">
    static SwitchCallback masterLASE = new SwitchCallback()
    {
        @Override
        public void changed(boolean value)
        {
            if (value)
            {
                try
                {
                    Robot robot = new Robot();
                    robot.keyPress(KeyEvent.VK_CONTROL);
                    robot.keyPress(KeyEvent.VK_L);
                    Thread.sleep(300);
                    robot.keyRelease(KeyEvent.VK_L);
                    robot.keyRelease(KeyEvent.VK_CONTROL);
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
            else
            {
                try
                {
                    Robot robot = new Robot();
                    robot.keyPress(KeyEvent.VK_SHIFT);
                    robot.keyPress(KeyEvent.VK_L);
                    Thread.sleep(300);
                    robot.keyRelease(KeyEvent.VK_L);
                    robot.keyRelease(KeyEvent.VK_SHIFT);
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
        }
    };// </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="lightsSwitch (pinkie) = CTRL+P (AS PANEL), SHIFT+P (OFF)">
    static SwitchCallback lightsSwitch = new SwitchCallback()
    {
        @Override
        public void changed(boolean value)
        {
            if (value)
            {
                try
                {
                    Robot robot = new Robot();
                    robot.keyPress(KeyEvent.VK_CONTROL);
                    robot.keyPress(KeyEvent.VK_P);
                    Thread.sleep(300);
                    robot.keyRelease(KeyEvent.VK_P);
                    robot.keyRelease(KeyEvent.VK_CONTROL);
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
            else
            {
                try
                {
                    Robot robot = new Robot();
                    robot.keyPress(KeyEvent.VK_SHIFT);
                    robot.keyPress(KeyEvent.VK_P);
                    Thread.sleep(300);
                    robot.keyRelease(KeyEvent.VK_P);
                    robot.keyRelease(KeyEvent.VK_SHIFT);
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
        }
    };// </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="gearUp = CTRL+G">
    static SwitchCallback gearUp = new SwitchCallback()
    {
        @Override
        public void changed(boolean value)
        {
            if (!value)
            {
                return;
            }
            try
            {
                Robot robot = new Robot();
                robot.keyPress(KeyEvent.VK_CONTROL);
                robot.keyPress(KeyEvent.VK_G);
                Thread.sleep(300);
                robot.keyRelease(KeyEvent.VK_G);
                robot.keyRelease(KeyEvent.VK_CONTROL);
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
            }
        }
    };// </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="gearDown = SHIFT+G">
    static SwitchCallback gearDown = new SwitchCallback()
    {
        @Override
        public void changed(boolean value)
        {
            if (!value)
            {
                return;
            }
            try
            {
                Robot robot = new Robot();
                robot.keyPress(KeyEvent.VK_SHIFT);
                robot.keyPress(KeyEvent.VK_G);
                Thread.sleep(300);
                robot.keyRelease(KeyEvent.VK_G);
                robot.keyRelease(KeyEvent.VK_SHIFT);
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
            }
        }
    };// </editor-fold>

    // This is how many milliseconds the fake keypress is held when the knob is
    // rotated by one click.
    private static final long KNOB_DELAY_HOLD_MS = 30;
    // RADIO PANEL BINDINGS
    // <editor-fold defaultstate="collapsed" desc="courseRight = SHIFT+COJNTROL+2">
    final static RadioCallback courseRight = new RadioCallback()
    {
        @Override
        public void changed(boolean value)
        {
            if (value)
            {
                synchronized (courseRight)
                {
                    try
                    {
                        Robot robot = new Robot();
                        robot.keyPress(KeyEvent.VK_SHIFT);
                        robot.keyPress(KeyEvent.VK_CONTROL);
                        robot.keyPress(KeyEvent.VK_2);
                        Thread.sleep(KNOB_DELAY_HOLD_MS);
                        robot.keyRelease(KeyEvent.VK_2);
                        robot.keyRelease(KeyEvent.VK_CONTROL);
                        robot.keyRelease(KeyEvent.VK_SHIFT);
                    }
                    catch (Exception ex)
                    {
                        ex.printStackTrace();
                    }
                }
            }
        }
    };// </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="courseLeft = SHIFT+COJNTROL+1">
    final static RadioCallback courseLeft = new RadioCallback()
    {
        @Override
        public void changed(boolean value)
        {
            if (value)
            {
                synchronized (courseLeft)
                {
                    try
                    {
                        Robot robot = new Robot();
                        robot.keyPress(KeyEvent.VK_SHIFT);
                        robot.keyPress(KeyEvent.VK_CONTROL);
                        robot.keyPress(KeyEvent.VK_1);
                        Thread.sleep(KNOB_DELAY_HOLD_MS);
                        robot.keyRelease(KeyEvent.VK_1);
                        robot.keyRelease(KeyEvent.VK_CONTROL);
                        robot.keyRelease(KeyEvent.VK_SHIFT);
                    }
                    catch (Exception ex)
                    {
                        ex.printStackTrace();
                    }
                }
            }
        }
    };// </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="headRight = SHIFT+COJNTROL+4">
    final static RadioCallback headRight = new RadioCallback()
    {
        @Override
        public void changed(boolean value)
        {
            if (value)
            {
                synchronized (headRight)
                {
                    try
                    {
                        Robot robot = new Robot();
                        robot.keyPress(KeyEvent.VK_SHIFT);
                        robot.keyPress(KeyEvent.VK_CONTROL);
                        robot.keyPress(KeyEvent.VK_4);
                        Thread.sleep(KNOB_DELAY_HOLD_MS);
                        robot.keyRelease(KeyEvent.VK_4);
                        robot.keyRelease(KeyEvent.VK_CONTROL);
                        robot.keyRelease(KeyEvent.VK_SHIFT);
                    }
                    catch (Exception ex)
                    {
                        ex.printStackTrace();
                    }
                }
            }
        }
    };// </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="headLeft = SHIFT+COJNTROL+3">
    final static RadioCallback headLeft = new RadioCallback()
    {
        @Override
        public void changed(boolean value)
        {
            if (value)
            {
                synchronized (headLeft)
                {
                    try
                    {
                        Robot robot = new Robot();
                        robot.keyPress(KeyEvent.VK_SHIFT);
                        robot.keyPress(KeyEvent.VK_CONTROL);
                        robot.keyPress(KeyEvent.VK_3);
                        Thread.sleep(KNOB_DELAY_HOLD_MS);
                        robot.keyRelease(KeyEvent.VK_3);
                        robot.keyRelease(KeyEvent.VK_CONTROL);
                        robot.keyRelease(KeyEvent.VK_SHIFT);
                    }
                    catch (Exception ex)
                    {
                        ex.printStackTrace();
                    }
                }
            }
        }
    };// </editor-fold>
}
