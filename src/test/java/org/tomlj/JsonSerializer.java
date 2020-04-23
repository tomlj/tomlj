/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.tomlj;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;

class JsonSerializer {
	
	@Test
	void shouldGenerateJsonArrayofMixedDataType(){
		String TOML = 
				"[array]\r\n" + 
				"key1 = [ 1993-08-04T15:05:00Z, 15:05:00, 2014-01-03T23:28:56.782Z, 15:06:00 ]";
		String ExpectedResult = 
				"{\r\n" + 
				"  \"array\" : {\r\n" + 
				"    \"key1\" : [\r\n" + 
				"      \"1993-08-04T15:05Z\",\r\n" + 
				"      \"15:05\",\r\n" + 
				"      \"2014-01-03T23:28:56.782Z\",\r\n" + 
				"      \"15:06\"\r\n" + 
				"    ]\r\n" + 
				"  }\r\n" + 
				"}\r\n" + 
				"\r\n" + 
				"";
		TomlParseResult toml = Toml.parse(TOML);
		String sJson = toml.toJson();
		
		assertEquals(ExpectedResult, sJson);
	}
	
	@Test
	void shouldGenerateJsonArrayofOffsetDateTimeDataType(){
		
		String TOML = "key1 = [ 1979-05-27T00:32:00.999999-07:00, 1979-05-27T00:32:00.999999-07:00, 1979-05-27T00:32:00.999999-07:00, 1979-05-27T00:32:00.999999-07:00 ]";
		String ExpectedResult = 
				"{\r\n" + 
				"  \"key1\" : [\r\n" + 
				"    \"1979-05-27T00:32:00.999999-07:00\",\r\n" + 
				"    \"1979-05-27T00:32:00.999999-07:00\",\r\n" + 
				"    \"1979-05-27T00:32:00.999999-07:00\",\r\n" + 
				"    \"1979-05-27T00:32:00.999999-07:00\"\r\n" + 
				"  ]\r\n" + 
				"}\r\n" + 
				"\r\n" + 
				"";
		
		TomlParseResult toml = Toml.parse(TOML);
		String sJson = toml.toJson();
		
		assertEquals(ExpectedResult, sJson);
	}
	
	@Test
	void shouldGenerateJsonArrayofLocalDateTimeDataType(){
		
		String TOML = "key1 = [1979-05-27T07:32:00, 1979-05-27T07:32:00, 1979-05-27T07:32:00, 1979-05-27T07:32:00 ]";
		String ExpectedResult = 
				"{\r\n" + 
				"  \"key1\": [\r\n" + 
				"    \"1979-05-27T07:32:00\",\r\n" + 
				"    \"1979-05-27T07:32:00\",\r\n" + 
				"    \"1979-05-27T07:32:00\",\r\n" + 
				"    \"1979-05-27T07:32:00\"\r\n" + 
				"  ]\r\n" + 
				"}\r\n" + 
				"\r\n" + 
				"";
		
		TomlParseResult toml = Toml.parse(TOML);
		String sJson = toml.toJson();
		
		assertEquals(ExpectedResult, sJson);
	}
	
	@Test
	void shouldGenerateJsonArrayofLocalDateDataType(){
		
		String TOML = "key1 = [1979-05-27, 1979-05-27, 1979-05-27, 1979-05-27 ]";
		String ExpectedResult = 
				"{\r\n" + 
				"  \"key1\": [\r\n" + 
				"    \"1979-05-27\",\r\n" + 
				"    \"1979-05-27\",\r\n" + 
				"    \"1979-05-27\",\r\n" + 
				"    \"1979-05-27\"\r\n" + 
				"  ]\r\n" + 
				"}\r\n" + 
				"\r\n" + 
				"";
		
		TomlParseResult toml = Toml.parse(TOML);
		String sJson = toml.toJson();
		
		assertEquals(ExpectedResult, sJson);
	}
	
	@Test
	void shouldGenerateJsonArrayofLocalTimeDataType(){
		
		String TOML = "key1 = [00:32:00.999999, 00:32:00.999999, 00:32:00.999999, 00:32:00.999999 ]";
		String ExpectedResult = 
				"{\r\n" + 
				"  \"key1\": [\r\n" + 
				"    \"00:32:00.999999\",\r\n" + 
				"    \"00:32:00.999999\",\r\n" + 
				"    \"00:32:00.999999\",\r\n" + 
				"    \"00:32:00.999999\"\r\n" + 
				"  ]\r\n" + 
				"}\r\n" + 
				"\r\n" + 
				"";
		
		TomlParseResult toml = Toml.parse(TOML);
		String sJson = toml.toJson();
		
		assertEquals(ExpectedResult, sJson);
	}
}
