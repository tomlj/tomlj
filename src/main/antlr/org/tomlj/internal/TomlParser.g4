parser grammar TomlParser;

options { tokenVocab=TomlLexer; superClass=AbstractTomlParser; contextSuperClass=AbstractTomlParserRuleContext; }

@header {
package org.tomlj.internal;
}

// Document parser
// Each line is an optional expression, so every decision here needs only one token of lookahead. That lets
// LineRecoveryStrategy skip the rest of a line that cannot be parsed, rather than a failed prediction discarding
// the rest of the document.
toml : expression? (NewLine expression?)* EOF;

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
array : ArrayStart (arrayValues NewLine* Comma?)? NewLine* ArrayEnd;
arrayValues : arrayValue (NewLine* Comma arrayValue)*;
arrayValue : NewLine* val;


// Table
table
  : standardTable
  | arrayTable
  ;


// Standard Table
standardTable : TableKeyStart key? TableKeyEnd;


// Inline Table
inlineTable : InlineTableStart (inlineTableValues NewLine* Comma?)? NewLine* InlineTableEnd;
inlineTableValues : inlineTableValue (NewLine* Comma inlineTableValue)*;
inlineTableValue : NewLine* keyval;


// Array Table
arrayTable : ArrayTableKeyStart key? ArrayTableKeyEnd;
