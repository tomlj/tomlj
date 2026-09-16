parser grammar TomlParser;

options { tokenVocab=TomlLexer; superClass=AbstractTomlParser; contextSuperClass=AbstractTomlParserRuleContext; }

@header {
package org.tomlj.internal;
}

// Document parser
// The lexer ends the last line of the input with a newline where the document has none, so every expression is
// followed by the end of its line. Each line starts with an expression, a comment or a newline, so every decision
// here needs only one token of lookahead, and LineRecoveryStrategy can skip the rest of a line that cannot be parsed.
toml : (expression Comment? NewLine | NewLine | commentRun)* EOF;

// A run of comment lines. It takes the newline that ends its last line, so the run is directly followed by whatever
// it is written above.
commentRun : Comment (NewLine Comment)* NewLine;

// The end of a line inside an array or an inline table, with the comment written on it, and the blank lines and
// comment runs that follow it.
lineBreak : Comment? NewLine (NewLine | commentRun)*;

expression
  : keyval
  | table
  ;


// Key string parser
tomlKey : key EOF;


// Key-Value pairs
keyval locals [int nesting] : key Equals val;

key : simpleKey (Dot simpleKey)*;
simpleKey
  : quotedKey
  | unquotedKey
  ;

unquotedKey : UnquotedKey;
quotedKey
  : basicString
  | literalString
  ;

val
  : {checkNestingDepth();}
    ( string
    | integer
    | floatValue
    | booleanValue
    | dateTime
    | array
    | inlineTable
    )
  ;


// String
string
  : mlBasicString
  | basicString
  | mlLiteralString
  | literalString
  ;


// Basic String
basicString : QuotationMark basicChar* QuotationMark;
basicChar
  : basicUnescaped
  | escaped
  ;
basicUnescaped : StringChars;

escaped : EscapeSequence;


// Multiline Basic String
mlBasicString : TripleQuotationMark mlBasicChar* TripleQuotationMark;
mlBasicChar
  : mlBasicUnescaped
  | escaped;
mlBasicUnescaped : StringChars;


// Literal String
literalString : Apostrophe literalBody Apostrophe;
literalBody : StringChars*;


// Multiline Literal String
mlLiteralString : TripleApostrophe mlLiteralBody TripleApostrophe;
mlLiteralBody : StringChars*;


// Integer
integer
     : decInt
     | hexInt
     | octInt
     | binInt
     ;

decInt : DecimalInteger;
hexInt : HexInteger;
octInt : OctalInteger;
binInt : BinaryInteger;


// Float
floatValue
  : regularFloat
  | regularFloatInf
  | regularFloatNaN
  ;
regularFloat : FloatingPoint;
regularFloatInf : FloatingPointInf;
regularFloatNaN : FloatingPointNaN;


// Boolean
booleanValue
  : trueBool
  | falseBool
  ;

trueBool : TrueBoolean;
falseBool : FalseBoolean;


// Date and Time
dateTime
 : offsetDateTime
 | localDateTime
 | localDate
 | localTime
 ;

offsetDateTime : date TimeDelimiter time timeOffset;
localDateTime : date TimeDelimiter time;
localDate : date;
localTime : time;

date : year Dash month Dash day;
time : hour Colon minute (Colon second (Dot secondFraction)?)?;
timeOffset
  : Z
  | hourOffset Colon minuteOffset
  ;
hourOffset : (Dash | Plus) hour;
minuteOffset : DateDigits;
secondFraction : DateDigits;
year : DateDigits;
month : DateDigits;
day : DateDigits;
hour : DateDigits;
minute : DateDigits;
second : DateDigits;


// Array
// A value or a comma is only reached after the newlines before it, so where the line after a newline cannot continue
// the array, prediction fails before the newline is consumed and LineRecoveryStrategy ends the array there.
array : ArrayStart (arrayValues (lineBreak? Comma)?)? lineBreak? ArrayEnd;
arrayValues : arrayValue (lineBreak? Comma arrayValue)*;
arrayValue : lineBreak? val;


// Table
table
  : standardTable
  | arrayTable
  ;


// Standard Table
standardTable : TableKeyStart key? TableKeyEnd;


// Inline Table
inlineTable : InlineTableStart (inlineTableValues (lineBreak? Comma)?)? lineBreak? InlineTableEnd;
inlineTableValues : inlineTableValue (lineBreak? Comma inlineTableValue)*;
inlineTableValue : lineBreak? keyval;


// Array Table
arrayTable : ArrayTableKeyStart key? ArrayTableKeyEnd;
