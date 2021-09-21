package io.github.noeppi_noeppi.tools.dye.loader.internal;

import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DescriptorParser {
    
    private static final Map<Character, Type> TYPES = Arrays.stream(Type.values()).collect(Collectors.toUnmodifiableMap(t -> t.sym, Function.identity()));

    public static Result parse(String descriptor) {
        Reader r = new StringReader(descriptor);
        try {
            if (r.read() != '(') throw new IOException("Expected ( at position 0");
            List<Entry> args = new ArrayList<>();
            while (true) {
                int read = r.read();
                if (read == -1)  throw new IOException("Unexpected end of input");
                if (read == ')') break;
                if (!TYPES.containsKey((char) read)) throw new IOException("Invalid descriptor arg type: " + (char) read);
                Type type = TYPES.get((char) read);
                if (type == Type.VOID) throw new IOException("Void is not allowed as argument");
                String ext = "";
                if (type.hasExt) {
                    ext = readUntil(r, ';');
                }
                args.add(new Entry(type, ext));
            }
            int read = r.read();
            if (read == -1) throw new IOException("Unexpected end of input");
            if (!TYPES.containsKey((char) read)) throw new IOException("Invalid descriptor ret type: " + (char) read);
            Type type = TYPES.get((char) read);
            String ext = "";
            if (type.hasExt) {
                ext = readUntil(r, ';');
            }
            if (r.read() != -1) throw new IOException("Input not fully consumed");
            return new Result(Collections.unmodifiableList(args), new Entry(type, ext));
        } catch (IOException e) {
            throw new RuntimeException("Dye: Parsing method descriptor: " + descriptor, e);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private static String readUntil(Reader reader, char chr) throws IOException {
        String str = readTo(reader, chr);
        return str.isEmpty() ? "" : str.substring(0, str.length() - 1);
    }

    private static String readTo(Reader reader, char chr) throws IOException {
        StringBuilder sb = new StringBuilder();
        while (true) {
            int read = reader.read();
            if (read == -1) return sb.toString();
            sb.append((char) read);
            if ((char) read == chr) return sb.toString();
        }
    }

    public static record Result(List<Entry> args, Entry ret) {

        @Override
        public String toString() {
            return "(" + args.stream().map(Entry::toString).collect(Collectors.joining("")) + ")" + ret;
        }
    }
    
    public static record Entry(Type type, String ext) {

        @Override
        public String toString() {
            return type.hasExt ? "" + type.sym  + ext + ";" : "" + type.sym;
        }
    }
    
    public enum Type {
        BOOLEAN('Z', 1, false, Opcodes.ILOAD, Opcodes.IRETURN),
        BYTE('B', 1, false, Opcodes.ILOAD, Opcodes.IRETURN),
        CHAR('C', 1, false, Opcodes.ILOAD, Opcodes.IRETURN),
        SHORT('S', 1, false, Opcodes.ILOAD, Opcodes.IRETURN),
        INTEGER('I', 1, false, Opcodes.ILOAD, Opcodes.IRETURN),
        LONG('J', 2, false, Opcodes.LLOAD, Opcodes.LRETURN),
        FLOAT('F', 1, false, Opcodes.FLOAD, Opcodes.FRETURN),
        DOUBLE('D', 2, false, Opcodes.DLOAD, Opcodes.DRETURN),
        REFERENCE('L', 1, true, Opcodes.ALOAD, Opcodes.ARETURN),
        VOID('V', 1, false, Opcodes.NOP, Opcodes.RETURN);
        
        public final char sym;
        public final int size;
        public final boolean hasExt;
        public final int opcodeLoad;
        public final int opcodeReturn;
        
        Type(char sym, int size, boolean hasExt, int opcodeLoad, int opcodeReturn) {
            this.sym = sym;
            this.size = size;
            this.hasExt = hasExt;
            this.opcodeLoad = opcodeLoad;
            this.opcodeReturn = opcodeReturn;
        }
    }
}
