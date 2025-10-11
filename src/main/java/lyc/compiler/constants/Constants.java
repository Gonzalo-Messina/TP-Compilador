package lyc.compiler.constants;
import java.math.BigDecimal;

public class Constants {
    public static final int MAX_LENGTH = 256;

    public static final int MAX_STRING_LITERAL_LENGTH = MAX_LENGTH;

    public static final int INT_MIN = Integer.MIN_VALUE;
    public static final int INT_MAX = Integer.MAX_VALUE;

    public static final BigDecimal FLOAT_ABS_MAX = new BigDecimal("1e38");
}
