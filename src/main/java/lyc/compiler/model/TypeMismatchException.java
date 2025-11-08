package lyc.compiler.model;

import java.io.Serial;

public class TypeMismatchException extends CompilerException {

  @Serial
  private static final long serialVersionUID = -8839023592847976069L;

  public TypeMismatchException(String message) {
    super("Type mismatch: " + message);
  }
}
