package com.maths22.directories;

import jdk.incubator.foreign.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
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
      CLinker.getInstance();
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
          CLinker.C_INT.withName("data1"),
          CLinker.C_SHORT.withName("data2"),
          CLinker.C_SHORT.withName("data3"),
          MemoryLayout.sequenceLayout(8, CLinker.C_CHAR).withName("data4")
  ).withName("GUID");

  static String getWinDir(UUID uuid) {
    System.loadLibrary("combase");
    System.loadLibrary("shell32");
    MethodHandle coTaskMemFree = CLinker.getInstance().downcallHandle(
            SymbolLookup.loaderLookup().lookup("CoTaskMemFree").get(),
            MethodType.methodType(void.class, MemoryAddress.class),
            FunctionDescriptor.ofVoid(CLinker.C_POINTER)
    );

    MethodHandle shGetKnownFolderPath = CLinker.getInstance().downcallHandle(
            SymbolLookup.loaderLookup().lookup("SHGetKnownFolderPath").get(),
            MethodType.methodType(int.class, MemorySegment.class, int.class, MemoryAddress.class, MemoryAddress.class),
            FunctionDescriptor.of(CLinker.C_INT, guidLayout, CLinker.C_INT, CLinker.C_POINTER, CLinker.C_POINTER)
    );

    try (var scope = ResourceScope.newConfinedScope()) {
      var allocator = SegmentAllocator.ofScope(scope);
      MemorySegment retVal = allocator.allocate(CLinker.C_POINTER);
      MemorySegment guid = allocator.allocate(guidLayout);

      int data1 = (int) ((uuid.getMostSignificantBits() >> (4 * 8)) & 0xFFFFFFFFL);
      MemoryAccess.setIntAtOffset(guid, guidLayout.byteOffset(MemoryLayout.PathElement.groupElement("data1")), data1);
      short data2 = (short) ((uuid.getMostSignificantBits() >> (2 * 8)) & 0xFFFFL);
      MemoryAccess.setShortAtOffset(guid, guidLayout.byteOffset(MemoryLayout.PathElement.groupElement("data2")), data2);
      short data3 = (short) (uuid.getMostSignificantBits() & 0xFFFFL);
      MemoryAccess.setIntAtOffset(guid, guidLayout.byteOffset(MemoryLayout.PathElement.groupElement("data3")), data3);
      byte data4a = (byte) ((uuid.getLeastSignificantBits() >> (7 * 8)) & 0xFFL);
      MemoryAccess.setByteAtOffset(guid, guidLayout.byteOffset(MemoryLayout.PathElement.groupElement("data4"), MemoryLayout.PathElement.sequenceElement(0)), data4a);
      byte data4b = (byte) ((uuid.getLeastSignificantBits() >> (6 * 8)) & 0xFFL);
      MemoryAccess.setByteAtOffset(guid, guidLayout.byteOffset(MemoryLayout.PathElement.groupElement("data4"), MemoryLayout.PathElement.sequenceElement(1)), data4b);
      for(int i = 2; i < 8; i++) {
        byte data4c = (byte) ((uuid.getLeastSignificantBits() >> ((7 - i) * 8)) & 0xFF);
        MemoryAccess.setByteAtOffset(guid, guidLayout.byteOffset(MemoryLayout.PathElement.groupElement("data4"), MemoryLayout.PathElement.sequenceElement(i)), data4c);
      }

      int invokeRes = (int) shGetKnownFolderPath.invokeExact(guid, 0, MemoryAddress.NULL, retVal.address());
      if(invokeRes != 0) {
        return null;
      }
      MemoryAddress returnedString = MemoryAccess.getAddress(retVal);
      int offset;
      // Loop until overflow to find null terminator of wchar_t string (presumably we reach practical limits first)
      //noinspection OverflowingLoopIndex
      for (offset = 0; offset >= 0; offset += 2) {
        short curr = MemoryAccess.getShortAtOffset(MemorySegment.globalNativeSegment(), returnedString.toRawLongValue() + offset);
        if (curr == 0) {
          break;
        }
      }
      MemorySegment segment = returnedString.asSegment(offset, scope);
      byte[] str = segment.toByteArray();
      String ret = new String(str, StandardCharsets.UTF_16LE);
      coTaskMemFree.invokeExact(returnedString);
      return ret;
    } catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }
}
