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
	void shouldPassEscapeChar() throws Exception {
		String TOML = "winpath = 'C:\\Users\\nodejs\\templates'";
		String ExpectedResult = 
				"{\r\n" + 
				"  \"winpath\" : \"C:\\\\Users\\\\nodejs\\\\templates\"\r\n" + 
				"}";
		TomlParseResult toml = Toml.parse(TOML);
		String sJson = toml.toJson();
		assertEquals(ExpectedResult, sJson);
	}
	
	@Test
	void shouldBeOrderedJson() throws Exception {
		String TOML = 
				"Name = { First = \"John\", Last = \"Doe\" }\r\n" + 
				"Company = { Name = \"GitHub\" }\r\n" + 
				"Phone = 8123456789\r\n" + 
				"DateOfBirth = 1993-08-04T15:05:00Z";
		
		String ExpectedResult = 
				"{\r\n" + 
				"  \"Name\" : {\r\n" + 
				"    \"First\" : \"John\",\r\n" + 
				"    \"Last\" : \"Doe\"\r\n" + 
				"  },\r\n" + 
				"  \"Company\" : {\r\n" + 
				"    \"Name\" : \"GitHub\"\r\n" + 
				"  },\r\n" + 
				"  \"Phone\" : 8123456789,\r\n" + 
				"  \"DateOfBirth\" : \"1993-08-04T15:05Z\"\r\n" + 
				"}";
		
		TomlParseResult toml = Toml.parse(TOML);
		String sJson = toml.toJson();
		assertEquals(ExpectedResult, sJson);
	}
}
