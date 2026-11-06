package org.jvmcpp.runtime.cli;

import org.jvmcpp.runtime.BenchmarkRuntime;
import org.jvmcpp.runtime.MemoryAllocator;
import org.jvmcpp.runtime.TestRuntime;

public final class Main {

    private static final String VERSION = "0.2.0";

    public static void main(String[] args) {
        if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h")) {
            printHelp();
            return;
        }

        switch (args[0]) {
            case "--version":
            case "-v":
                System.out.println("jvm-cpp-runtime version " + VERSION);
                break;
            case "--test":
            case "-t":
                System.out.println("[CLI] Running runtime test harness...");
                TestRuntime.main(new String[0]);
                break;
            case "--bench":
            case "-b":
                System.out.println("[CLI] Executing benchmark suite...");
                BenchmarkRuntime.main(new String[0]);
                break;
            case "--info":
            case "-i":
                printInfo();
                break;
            default:
                System.err.println("Unknown option: " + args[0]);
                printHelp();
                System.exit(1);
        }
    }

    private static void printHelp() {
        System.out.println("jvm-cpp-runtime CLI runner");
        System.out.println("Usage: java org.jvmcpp.runtime.cli.Main [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --test, -t       Run the TestRuntime test harness");
        System.out.println("  --bench, -b      Run the BenchmarkRuntime performance suite");
        System.out.println("  --info, -i       Display runtime memory and allocator information");
        System.out.println("  --version, -v    Display current runtime version");
        System.out.println("  --help, -h       Display this help message");
    }

    private static void printInfo() {
        MemoryAllocator allocator = new MemoryAllocator();
        System.out.println("JVM-CPP-RUNTIME INFO:");
        System.out.println("  Java Version:         " + System.getProperty("java.version"));
        System.out.println("  OS Name:              " + System.getProperty("os.name"));
        System.out.println("  Runtime Version:      " + VERSION);
        System.out.println("  Max Heap Quota:       " + (allocator.getMaxHeapBytes() == Long.MAX_VALUE ? "Unlimited" : allocator.getMaxHeapBytes() + " bytes"));
        System.out.println("  Active Allocations:   " + allocator.getActiveAllocationCount());
        System.out.println("  Total Allocated Bytes:" + allocator.getTotalAllocatedBytes());
    }
}
