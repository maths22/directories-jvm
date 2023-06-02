package com.maths22.directories;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class WindowsNativeUtil {
  static final String POWERSHELL_WARNING_PROPERTY = "com.maths22.directories.powershellWarning";
  static final String POWERSHELL_WARNING_FOOTER = " (or silence with -D" + POWERSHELL_WARNING_PROPERTY + "=disabled)";

  private WindowsNativeUtil() {
    throw new Error();
  }

  static boolean isSupported() {
    try {
      Linker.nativeLinker();
      return true;
    } catch (IllegalCallerException ex) {
      if(!"disabled".equals(System.getProperty(POWERSHELL_WARNING_PROPERTY))) {
        System.err.println("WARN: Native access not available to com.maths22.directories, falling back to less reliable methods. " +
                           "Please grant with `--enable-native-access=ALL-UNNAMED`" + POWERSHELL_WARNING_FOOTER);
      }
      return false;
    }
  }

  private static final MemoryLayout guidLayout = MemoryLayout.structLayout(
          ValueLayout.JAVA_INT.withName("data1"),
          ValueLayout.JAVA_SHORT.withName("data2"),
          ValueLayout.JAVA_SHORT.withName("data3"),
          MemoryLayout.sequenceLayout(8, ValueLayout.JAVA_BYTE).withName("data4")
  ).withName("GUID");

  static String getWinDir(UUID uuid) {
    System.loadLibrary("combase");
    System.loadLibrary("shell32");
    MethodHandle coTaskMemFree = Linker.nativeLinker().downcallHandle(
            SymbolLookup.loaderLookup().lookup("CoTaskMemFree").get(),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
    );

    MethodHandle shGetKnownFolderPath = Linker.nativeLinker().downcallHandle(
            SymbolLookup.loaderLookup().lookup("SHGetKnownFolderPath").get(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, guidLayout, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS)
    );

    try (var scope = MemorySession.openConfined()) {
      var allocator = SegmentAllocator.newNativeArena(scope);
      MemorySegment retVal = allocator.allocate(ValueLayout.ADDRESS);
      MemorySegment guid = allocator.allocate(guidLayout);

      int data1 = (int) ((uuid.getMostSignificantBits() >> (4 * 8)) & 0xFFFFFFFFL);
      guid.set(ValueLayout.JAVA_INT, guidLayout.byteOffset(MemoryLayout.PathElement.groupElement("data1")), data1);
      short data2 = (short) ((uuid.getMostSignificantBits() >> (2 * 8)) & 0xFFFFL);
      guid.set(ValueLayout.JAVA_SHORT, guidLayout.byteOffset(MemoryLayout.PathElement.groupElement("data2")), data2);
      short data3 = (short) (uuid.getMostSignificantBits() & 0xFFFFL);
      guid.set(ValueLayout.JAVA_SHORT, guidLayout.byteOffset(MemoryLayout.PathElement.groupElement("data3")), data3);
      byte data4a = (byte) ((uuid.getLeastSignificantBits() >> (7 * 8)) & 0xFFL);
      guid.set(ValueLayout.JAVA_BYTE, guidLayout.byteOffset(MemoryLayout.PathElement.groupElement("data4"), MemoryLayout.PathElement.sequenceElement(0)), data4a);
      byte data4b = (byte) ((uuid.getLeastSignificantBits() >> (6 * 8)) & 0xFFL);
      guid.set(ValueLayout.JAVA_BYTE, guidLayout.byteOffset(MemoryLayout.PathElement.groupElement("data4"), MemoryLayout.PathElement.sequenceElement(1)), data4b);
      for(int i = 2; i < 8; i++) {
        byte data4c = (byte) ((uuid.getLeastSignificantBits() >> ((7 - i) * 8)) & 0xFF);
        guid.set(ValueLayout.JAVA_BYTE, guidLayout.byteOffset(MemoryLayout.PathElement.groupElement("data4"), MemoryLayout.PathElement.sequenceElement(i)), data4c);
      }

      int invokeRes = (int) shGetKnownFolderPath.invoke(guid, 0, MemoryAddress.NULL, retVal.address());
      if(invokeRes != 0) {
        return null;
      }
      MemoryAddress returnedString = retVal.get(ValueLayout.ADDRESS, 0);
      int offset;
      // Loop until overflow to find null terminator of wchar_t string (presumably we reach practical limits first)
      //noinspection OverflowingLoopIndex
      for (offset = 0; offset >= 0; offset += 2) {
        short curr = returnedString.get(ValueLayout.JAVA_BYTE, offset);
        if (curr == 0) {
          break;
        }
      }
      MemorySegment segment = MemorySegment.ofAddress(returnedString, offset, scope);
      byte[] str = segment.toArray(ValueLayout.JAVA_BYTE);
      String ret = new String(str, StandardCharsets.UTF_16LE);
      coTaskMemFree.invoke(returnedString);
      return ret;
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }
}
