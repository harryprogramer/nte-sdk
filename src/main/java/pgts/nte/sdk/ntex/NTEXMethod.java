package pgts.nte.sdk.ntex;

import java.util.Map;

public class NTEXMethod {
    private final String name;
    private final Map<String, String> params;
    private final boolean isExternal;
    private final String returnType;
    private final int line;

    public enum ArithmeticOperator{
        PLUS('+'),
        MINUS('-'),
        DIVIDE('/'),
        MULTIPLY('*'),
        MODULO('%')
        ;

        public final char symbol;

        ArithmeticOperator(char symbol){
            this.symbol = symbol;
        }
    }

    public NTEXMethod(String name, Map<String, String> params, boolean isExternal, String returnType, int line){
        this.name = name;
        this.params = params;
        this.isExternal = isExternal;
        this.returnType = returnType;
        this.line = line;
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public boolean isExternal() {
        return isExternal;
    }

    public int getLine() {
        return line;
    }

    public String getReturnType() {
        return returnType;
    }
}