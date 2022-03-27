package pgts.nte.sdk.ntex;

import java.util.Map;

public class NTEXMethod {
    private final String name;
    private final Map<String, String> params;
    private final boolean isExternal;
    private final String returnType;

    public NTEXMethod(String name, Map<String, String> params, boolean isExternal, String returnType){
        this.name = name;
        this.params = params;
        this.isExternal = isExternal;
        this.returnType = returnType;
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

    public String getReturnType() {
        return returnType;
    }
}