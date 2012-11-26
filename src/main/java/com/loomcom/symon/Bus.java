package com.loomcom.symon;

import java.util.*;

import com.loomcom.symon.devices.*;
import com.loomcom.symon.exceptions.*;

/**
 * The Bus ties the whole thing together, man.
 */
public class Bus {

    // The default address at which to load programs
    public static int DEFAULT_LOAD_ADDRESS = 0x0200;
	
    // By default, our bus starts at 0, and goes up to 64K
    private int startAddress = 0x0000;
    private int endAddress = 0xffff;

    // The CPU
    private Cpu cpu;

    // Ordered list of IO devices.
    private SortedSet<Device> devices;

    public Bus(int size) {
        this(0, size - 1);
    }

    public Bus(int startAddress, int endAddress) {
        this.devices = new TreeSet<Device>();
        this.startAddress = startAddress;
        this.endAddress = endAddress;
    }

    public int startAddress() {
        return startAddress;
    }

    public int endAddress() {
        return endAddress;
    }

    /**
     * Add a device to the bus. Throws a MemoryRangeException if the device overlaps with any others.
     *
     * @param device
     * @throws MemoryRangeException
     */
    public void addDevice(Device device) throws MemoryRangeException {
        // Make sure there's no memory overlap.
        MemoryRange memRange = device.getMemoryRange();
        for (Device d : devices) {
            if (d.getMemoryRange().overlaps(memRange)) {
                throw new MemoryRangeException("The device being added at " +
                                               String.format("$%04X", memRange.startAddress()) +
                                               " overlaps with an existing " +
                                               "device, '" + d + "'");
            }
        }

        // Add the device
        device.setBus(this);
        devices.add(device);
    }

    /**
     * Remove a device from the bus.
     *
     * @param device
     */
    public void removeDevice(Device device) {
        if (devices.contains(device)) {
            devices.remove(device);
        }
    }

    public void addCpu(Cpu cpu) {
        this.cpu = cpu;
        cpu.setBus(this);
    }

    /**
     * Returns true if the memory map is full, i.e., there are no
     * gaps between any IO devices.  All memory locations map to some
     * device.
     */
    public boolean isComplete() {
        // Empty maps cannot be complete.
        if (devices.isEmpty()) {
            return false;
        }

        // Loop over devices and ensure they are contiguous.
        MemoryRange prev = null;
        int i = 0;
        int length = devices.size();
        for (Device d : devices) {
            MemoryRange cur = d.getMemoryRange();
            if (i == 0) {
                // If the first entry doesn't start at 'startAddress', return false.
                if (cur.startAddress() != startAddress) {
                    return false;
                }
            }

            if (prev != null && i < length - 1) {
                // Otherwise, compare previous map's end against this map's
                // endAddress.  They must be adjacent!
                if (cur.startAddress() - 1 != prev.endAddress()) {
                    return false;
                }
            }

            if (i == length - 1) {
                // If the last entry doesn't end at endAddress, return false;
                if (cur.endAddress() != endAddress) {
                    return false;
                }
            }

            i++;
            prev = cur;
        }

        // Must be complete.
        return true;
    }

    public int read(int address) throws MemoryAccessException {
        for (Device d : devices) {
            MemoryRange range = d.getMemoryRange();
            if (range.includes(address)) {
                // Compute offset into this device's address space.
                int devAddr = address - range.startAddress();
                return d.read(devAddr);
            }
        }
        throw new MemoryAccessException("Bus read failed. No device at address " + String.format("$%04X", address));
    }

    public void write(int address, int value) throws MemoryAccessException {
        for (Device d : devices) {
            MemoryRange range = d.getMemoryRange();
            if (range.includes(address)) {
                // Compute offset into this device's address space.
                int devAddr = address - range.startAddress();
                d.write(devAddr, value);
                return;
            }
        }
        throw new MemoryAccessException("Bus write failed. No device at address " + String.format("$%04X", address));
    }

    public SortedSet<Device> getDevices() {
        // Expose a copy of the device list, not the original
        return new TreeSet<Device>(devices);
    }

    public Cpu getCpu() {
        return cpu;
    }

    public void loadProgram(int... program) throws MemoryAccessException {
        int address = getCpu().getProgramCounter();
        int i = 0;
        for (int d : program) {
            write(address + i++, d);
        }
    }
}
