package alfusos.rpc;

/** Compile-only declaration. The car supplies the real class from its boot framework. */
public final class RpcManager {
    private RpcManager() { }

    public static RpcManager getInstance() {
        throw new UnsupportedOperationException("compile-only stub");
    }

    public byte[] getMessage(int opcode, byte[] data) {
        throw new UnsupportedOperationException("compile-only stub");
    }
}
