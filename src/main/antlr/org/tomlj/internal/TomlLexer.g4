lexer grammar TomlLexer;

channels { COMMENTS, WHITESPACE }

tokens { TripleQuotationMark, TripleApostrophe, StringChar, Comma }

@header {
package org.tomlj.internal;
}

@members {
  // State is made public to allow incremental lexers to save and restore it (e.g. NetBeans)
  public final IntegerStack arrayDepthStack = new IntegerStack();
  public int arrayDepth = 0;

  private boolean inArray() {
    return arrayDepth > 0;
  }

  private void pushValueModeIfInArray() {
    if (inArray()) {
        pushMode(ValueMode);
    }
  }

  private void resetArrayDepth() {
    arrayDepthStack.clear();
    arrayDepth = 0;
  }

  private void pushArrayDepth() {
    arrayDepthStack.push(arrayDepth);
    arrayDepth = 0;
  }

  private void popArrayDepth() {
    arrayDepth = arrayDepthStack.pop();
  }
}

fragment WSChar : [ \t];
fragment NL : '\r'? '\n';
fragment COMMENT : '#' (~[\u0000-\u0008\u000A-\u001F\u007F\uD800-\uDFFF])*;
fragment Alpha : [A-Za-z];
fragment Digit : [0-9];
fragment Digit1_9 : [1-9];
fragment Digit0_7 : [0-7];
fragment Digit0_1 : [0-1];
fragment HexDig : Digit | [A-Fa-f];

fragment KeyChar : (Alpha | Digit | '-' | '_');
fragment UNQUOTED_KEY : KeyChar+;
fragment LENIENT_UNQUOTED_KEY : KeyChar | KeyChar (KeyChar | WSChar)* KeyChar;

Dot : '.';
Equals : '=' { resetArrayDepth(); } -> pushMode(ValueMode);
QuotationMark : '"' -> pushMode(BasicStringMode);
Apostrophe : '\'' -> pushMode(LiteralStringMode);
TableKeyStart : '[';
TableKeyEnd : ']';
ArrayTableKeyStart : '[[';
ArrayTableKeyEnd : ']]';
UnquotedKey : UNQUOTED_KEY;

WS : WSChar+ -> channel(WHITESPACE);
Comment : COMMENT -> channel(COMMENTS);
NewLine : NL;
Error : .;

mode TomlKeyMode;

TomlKeyDot : '.' -> type(Dot);
TomlKeyQuotationMark : '"' -> type(QuotationMark), pushMode(BasicStringMode);
TomlKeyApostrophe : '\'' -> type(Apostrophe), pushMode(LiteralStringMode);
TomlKeyUnquotedKey : LENIENT_UNQUOTED_KEY -> type(UnquotedKey);

TomlKeyWS : WSChar+ -> type(WS), channel(WHITESPACE);
TomlKeyError : . -> type(Error);

mode ValueMode;

// Strings
ValueQuotationMark : '"' { pushValueModeIfInArray(); } -> type(QuotationMark), mode(BasicStringMode);
ValueTripleQuotationMark : '"""' NL? { pushValueModeIfInArray(); } -> type(TripleQuotationMark), mode(MLBasicStringMode);
ValueApostrophe : '\'' { pushValueModeIfInArray(); } -> type(Apostrophe), mode(LiteralStringMode);
ValueTripleApostrophe : '\'\'\'' NL? { pushValueModeIfInArray(); } -> type(TripleApostrophe), mode(MLLiteralStringMode);

// Integers
fragment DecInt : [-+]? (Digit | Digit1_9 ('_'? Digit)+);
DecimalInteger : DecInt { "-:".indexOf(_input.LA(1)) < 0 }? { pushValueModeIfInArray(); } -> popMode;
HexInteger : '0x' HexDig ('_'? HexDig)* { pushValueModeIfInArray(); } -> popMode;
OctalInteger : '0o' Digit0_7 ('_'? Digit0_7)* { pushValueModeIfInArray(); } -> popMode;
BinaryInteger : '0b' Digit0_1 ('_'? Digit0_1)* { pushValueModeIfInArray(); } -> popMode;

// Float
fragment Exp : [eE] [-+]? Digit ('_'? Digit)*;
fragment Frac : '.' Digit ('_'? Digit)*;
FloatingPoint : DecInt (Exp | Frac Exp?) { pushValueModeIfInArray(); } -> popMode;
FloatingPointInf: [-+]? 'inf' { pushValueModeIfInArray(); } -> popMode;
FloatingPointNaN : [-+]? 'nan' { pushValueModeIfInArray(); } -> popMode;

// Boolean
TrueBoolean : 'true' { pushValueModeIfInArray(); } -> popMode;
FalseBoolean : 'false' { pushValueModeIfInArray(); } -> popMode;

// Date and Time
DateStart : Digit+ { "-:".indexOf(_input.LA(1)) >= 0 }? { pushValueModeIfInArray(); } -> type(DateDigits), mode(DateMode);

// Array
ArrayStart : '[' { arrayDepth++; };
ArrayEnd : ']' { if (inArray()) { arrayDepth--; pushValueModeIfInArray(); } } -> popMode;

// Table
InlineTableStart : '{' { pushValueModeIfInArray(); pushArrayDepth(); } -> mode(InlineTableMode);

ValueComma : ',' -> type(Comma);
// A newline outside an array ends the key/value pair, so the value is missing and the mode must be left.
ValueNewLine: NL { if (!inArray()) { popMode(); } } -> type(NewLine);
ValueWS : WSChar+ -> type(WS), channel(WHITESPACE);
ValueComment : COMMENT -> type(Comment), channel(COMMENTS);

ValueError : . -> type(Error), popMode;


mode BasicStringMode;

BasicStringEnd : '"' -> type(QuotationMark), popMode;
BasicStringUnescaped : ~[\u0000-\u0008\u000A-\u001F"\\\u007F\uD800-\uDFFF] -> type(StringChar);
EscapeSequence
  : '\\' ~[\n]
  | '\\x' HexDig HexDig
  | '\\u' HexDig HexDig HexDig HexDig
  | '\\U' HexDig HexDig HexDig HexDig HexDig HexDig HexDig HexDig;

BasicStringNewLine: NL -> type(NewLine), popMode;
BasicStringError : . -> type(Error), popMode;


mode MLBasicStringMode;

MLBasicStringSextEnd : '"""' { _input.LA(1) == '"' && _input.LA(2) == '"' && _input.LA(3) == '"' }? -> type(TripleQuotationMark), popMode;
MLBasicStringEnd : '"""' { _input.LA(1) != '"' }? -> type(TripleQuotationMark), popMode;
// A backslash ending a line continues the string over the newlines and whitespace that follow, so it keeps a token type
// of its own: code that finds the lines of a document by the NewLine type must not mistake it for the end of a line.
MLBasicStringLineEndBackslash : '\\' WSChar* NL (WSChar | NL)* -> channel(WHITESPACE);
MLBasicStringUnescaped : ~[\u0000-\u0008\u000A-\u001F\\\u007F\uD800-\uDFFF] -> type(StringChar);
MLBasicStringEscape :
  ('\\x' HexDig HexDig
  | '\\u' HexDig HexDig HexDig HexDig
  | '\\U' HexDig HexDig HexDig HexDig HexDig HexDig HexDig HexDig
  | '\\' .) -> type(EscapeSequence);
// TOML lets a parser choose the newline in a multi-line string value. Both LF and CRLF become "\n", so a document
// parses to the same values on every platform and whichever line endings the file uses.
MLBasicStringNewLine: NL { setText("\n"); } -> type(StringChar);

MLBasicStringError : . -> type(Error), popMode;


mode LiteralStringMode;

LiteralStringEnd : '\'' -> type(Apostrophe), popMode;
LiteralStringChar : ~[\u0000-\u0008\u000A-\u001F'\u007F\uD800-\uDFFF] -> type(StringChar);

LiteralStringNewLine: NL -> type(NewLine), popMode;
LiteralStringError : . -> type(Error), popMode;


mode MLLiteralStringMode;

MLLiteralStringSextEnd : '\'\'\'' { _input.LA(1) == '\'' && _input.LA(2) == '\'' && _input.LA(3) == '\'' }? -> type(TripleApostrophe), popMode;
MLLiteralStringEnd : '\'\'\'' { _input.LA(1) != '\'' }? -> type(TripleApostrophe), popMode;
MLLiteralStringChar : ~[\u0000-\u0008\u000A-\u001F\u007F\uD800-\uDFFF] -> type(StringChar);
// Newlines are normalized as in a multi-line basic string.
MLLiteralStringNewLine: NL { setText("\n"); } -> type(StringChar);

MLLiteralStringError : . -> type(Error), popMode;


mode DateMode;

Dash : '-';
Plus : '+';
Colon : ':';
DateDot : '.' -> type(Dot);
Z : ('Z' | 'z');
TimeDelimiter : [Tt] | (' ' { _input.LA(1) >= '0' && _input.LA(1) <= '9' }?);
DateDigits : Digit+;

DateWS : WSChar+ -> type(WS), channel(WHITESPACE), popMode;
DateComment : COMMENT -> type(Comment), channel(COMMENTS), popMode;
DateNewLine: NL -> type(NewLine), popMode;
DateComma: ',' -> type(Comma), popMode;
// DateStart pushed ValueMode inside an array, so leave it before closing the array as ArrayEnd does.
DateArrayEnd : ']' { if (inArray()) { popMode(); arrayDepth--; pushValueModeIfInArray(); } } -> type(ArrayEnd), popMode;
// A date directly before the end of an inline table leaves DateMode, then closes the table as InlineTableEnd does.
DateInlineTableEnd : '}' { !_modeStack.isEmpty() && _modeStack.peek() == InlineTableMode }? { popMode(); popArrayDepth(); } -> type(InlineTableEnd), popMode;
DateError : . -> type(Error), popMode;


mode InlineTableMode;

InlineTableEnd : '}' { popArrayDepth(); } -> popMode;
InlineTableDot : '.' -> type(Dot);
InlineTableEquals : '=' -> type(Equals), pushMode(ValueMode);
InlineTableComma : ',' -> type(Comma);
InlineTableQuotationMark : '"' -> type(QuotationMark), pushMode(BasicStringMode);
InlineTableApostrophe : '\'' -> type(Apostrophe), pushMode(LiteralStringMode);
InlineTableUnquotedKey : UNQUOTED_KEY -> type(UnquotedKey);

InlineTableWS : WSChar+ -> type(WS), channel(WHITESPACE);
InlineTableComment : COMMENT -> type(Comment), channel(COMMENTS);
InlineTableNewLine : NL -> type(NewLine);
InlineTableError : . -> type(Error), popMode;
