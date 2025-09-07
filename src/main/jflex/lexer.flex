package lyc.compiler;

import java_cup.runtime.Symbol;
import lyc.compiler.ParserSym;
import lyc.compiler.model.*;
import lyc.compiler.constants.Constants;
import lyc.compiler.files.SymbolTableGenerator;

%%

%public
%class Lexer
%unicode
%cup
%line
%column
%throws CompilerException
%eofval{
  return symbol(ParserSym.EOF);
%eofval}


%{
  private Symbol symbol(int type) {
    return new Symbol(type, yyline, yycolumn);
  }
  private Symbol symbol(int type, Object value) {
    return new Symbol(type, yyline, yycolumn, value);
  }
  private void saveToken() {
  	SymbolTableGenerator.getInstance().addToken(yytext());
  }
  private void saveTokenCTE(String dataType){
  	  	SymbolTableGenerator.getInstance().addToken(yytext(),dataType);
  }
  private boolean isValidStringLength() {
  	return yylength() <= Constants.MAX_STRING_LITERAL_LENGTH;
  }
%}


LineTerminator = \r|\n|\r\n
InputCharacter = [^\r\n]
Identation =  [ \t\f]

Plus = "+"
Mult = "*"
Sub = "-"
Div = "/"
Assig = ":="
Eq = "="
Gt = ">"
Lt = "<"
Ge = ">="
Le = "<="
And = "AND"
Or = "OR"
Not = "NOT"

OpenBracket = "("
CloseBracket = ")"
OpenBrace = "{"
CloseBrace = "}"

Letter = [a-zA-Z]
Digit = [0-9]

Comma = ","
Colon = ":"

WhiteSpace = {LineTerminator} | {Identation}
Identifier = {Letter} ({Letter}|{Digit})*
IntegerConstant = {Digit}+
FloatConstant    = {Digit}+"."{Digit}*|("."{Digit}+)
Text =	[\"].*[\"]

Read = "read"
Write = "write"

/*Data Types */

TypeInt          = "Int"
TypeFloat        = "Float"
TypeString       = "String"

Init             = "Init"


%%


/* keywords */



<YYINITIAL> {

 /* PALABRAS RESERVADAS */
 {Read}           { return symbol(ParserSym.READ); }
 {Write}          { return symbol(ParserSym.WRITE); }
 {Init}           { return symbol(ParserSym.INIT); }
 {TypeInt}        { return symbol(ParserSym.TYPE_INT); }
 {TypeFloat}      { return symbol(ParserSym.TYPE_FLOAT); }
 {TypeString}     { return symbol(ParserSym.TYPE_STRING); }


 /* IDENTIFICADOR */
 {Identifier}     { return symbol(ParserSym.IDENTIFIER, yytext()); }


 /* CONSTANTES Y LITERALES */
 {IntegerConstant} { saveTokenCTE("Int"); return symbol(ParserSym.INTEGER_CONSTANT, yytext()); }
 {FloatConstant}   { saveTokenCTE("Float"); return symbol(ParserSym.FLOAT_CONSTANT, yytext()); }
 {Text}            {
                     if(!isValidStringLength())
                       throw new InvalidLengthException("\"" + yytext() + "\""+ " string length not allowed");
                     saveTokenCTE("string");
                     return symbol(ParserSym.TEXT, yytext());
                   }


 /* OPERADORES ARITMÉTICOS Y DE ASIGNACIÓN */
 {Plus}           { return symbol(ParserSym.PLUS); }
 {Sub}            { return symbol(ParserSym.SUB); }
 {Mult}           { return symbol(ParserSym.MULT); }
 {Div}            { return symbol(ParserSym.DIV); }
 {Assig}          { return symbol(ParserSym.ASSIG); }


 /* OPERADORES RELACIONALES */
 {Eq}             { return symbol(ParserSym.EQ); }
 {Gt}             { return symbol(ParserSym.GT); }
 {Lt}             { return symbol(ParserSym.LT); }
 {Ge}             { return symbol(ParserSym.GE); }
 {Le}             { return symbol(ParserSym.LE); }

 /* OPERADORES LÓGICOS */
 {And}            { return symbol(ParserSym.AND); }
 {Or}             { return symbol(ParserSym.OR); }
 {Not}            { return symbol(ParserSym.NOT); }


 /* SÍMBOLOS DE PUNTUACIÓN Y AGRUPACIÓN */
 {Comma}          { return symbol(ParserSym.COMMA); }
 {Colon}          { return symbol(ParserSym.COLON); }
 {OpenBracket}    { return symbol(ParserSym.OPEN_BRACKET); }
 {CloseBracket}   { return symbol(ParserSym.CLOSE_BRACKET); }
 {OpenBrace}      { return symbol(ParserSym.OPEN_BRACE); }
 {CloseBrace}     { return symbol(ParserSym.CLOSE_BRACE); }


 /* ESPACIOS EN BLANCO (IGNORAR) */
 {WhiteSpace}     { /* ignore */ }

}

/* ========================================================================== */

/* error fallback */
[^]               { throw new UnknownCharacterException(yytext()); }