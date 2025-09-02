package lyc.compiler.files;

import lyc.compiler.model.CompilerException;
import lyc.compiler.model.DuplicateVariableException;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SymbolTableGenerator implements FileGenerator {
    private static SymbolTableGenerator symbolTable;

    private Map<String,SymbolTableData> symbols;
    private SymbolTableGenerator() {
        this.symbols = new HashMap<String,SymbolTableData>();
    }
    public static SymbolTableGenerator getInstance() {
        if(symbolTable == null) {
            symbolTable = new SymbolTableGenerator();
        }
        return symbolTable;
    }
    @Override
    public void generate(FileWriter fileWriter) throws IOException {
        String file = String.format("%-30s|%-30s|%-30s|%-30s\n", "NOMBRE", "TIPODATO", "VALOR", "LONGITUD");
        for (Map.Entry<String, SymbolTableData> entry : this.symbols.entrySet()) {
            file += String.format("%-30s", entry.getKey()) + "|" + entry.getValue().toString() + "\n";
        }
        fileWriter.write(file);
    }
    public void addToken(String token) {
        if(!this.symbols.containsKey(token)) {
            this.symbols.put(token,new SymbolTableData());
        }
    }

    public void addToken(String token,String dataType) {
        if(!this.symbols.containsKey(token)) {
            SymbolTableData data = new SymbolTableData(dataType,token,Integer.toString(token.length()-1));
            this.symbols.put("_" + token,data);
        }
    }

    public void addToken(String token,String dataType,String value) {
        if(!this.symbols.containsKey(token)) {
            SymbolTableData data = new SymbolTableData(dataType,token,value);
            this.symbols.put("_" + token,data);
        }
    }

    public void addDataType(String id, String dataType) throws CompilerException {
        SymbolTableData data = this.symbols.get(id);
        if(data.getType() != null){
            throw new DuplicateVariableException("Variable " + id + " ya definida");
        }
        data.setType(dataType);
    }
}
