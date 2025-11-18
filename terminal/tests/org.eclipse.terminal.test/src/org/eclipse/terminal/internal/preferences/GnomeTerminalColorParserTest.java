/*******************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.terminal.internal.preferences;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import org.eclipse.swt.graphics.RGB;
import org.eclipse.terminal.internal.preferences.GnomeTerminalColorParser.GnomeTerminalColors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GnomeTerminalColorParserTest {

	@Rule
	public TemporaryFolder tempFolder = new TemporaryFolder();

	@Test
	public void testParseValidSchema() throws IOException {
		String schemaContent = """
				<?xml version="1.0" encoding="UTF-8"?>
				<schemalist>
				  <schema id="org.gnome.Terminal.Legacy.Profile">
				    <key name="palette" type="as">
				      <default>['rgb(0,0,0)', 'rgb(205,0,0)', 'rgb(0,205,0)', 'rgb(205,205,0)', 'rgb(0,0,238)', 'rgb(205,0,205)', 'rgb(0,205,205)', 'rgb(229,229,229)', 'rgb(127,127,127)', 'rgb(255,0,0)', 'rgb(0,255,0)', 'rgb(255,255,0)', 'rgb(92,92,255)', 'rgb(255,0,255)', 'rgb(0,255,255)', 'rgb(255,255,255)']</default>
				    </key>
				    <key name="foreground-color" type="s">
				      <default>'rgb(255,255,255)'</default>
				    </key>
				    <key name="background-color" type="s">
				      <default>'rgb(0,0,0)'</default>
				    </key>
				    <key name="highlight-colors-set" type="b">
				      <default>false</default>
				    </key>
				    <key name="highlight-foreground-color" type="s">
				      <default>'rgb(0,0,0)'</default>
				    </key>
				    <key name="highlight-background-color" type="s">
				      <default>'rgb(255,255,255)'</default>
				    </key>
				  </schema>
				</schemalist>
				""";

		File schemaFile = tempFolder.newFile("test-schema.xml");
		Files.writeString(schemaFile.toPath(), schemaContent);

		Optional<GnomeTerminalColors> result = GnomeTerminalColorParser.parse(schemaFile.getAbsolutePath());

		assertTrue("Parser should successfully parse valid schema", result.isPresent());
		GnomeTerminalColors colors = result.get();

		// Check palette
		List<RGB> palette = colors.getPalette();
		assertEquals("Palette should have 16 colors", 16, palette.size());
		assertEquals("First color should be black", new RGB(0, 0, 0), palette.get(0));
		assertEquals("Second color should be red", new RGB(205, 0, 0), palette.get(1));
		assertEquals("Last color should be white", new RGB(255, 255, 255), palette.get(15));

		// Check foreground and background
		assertEquals("Foreground should be white", new RGB(255, 255, 255), colors.getForeground());
		assertEquals("Background should be black", new RGB(0, 0, 0), colors.getBackground());

		// When highlight-colors-set is false, selection colors should default to foreground/background
		assertEquals("Selection foreground should default to background", new RGB(0, 0, 0),
				colors.getSelectionForeground());
		assertEquals("Selection background should default to foreground", new RGB(255, 255, 255),
				colors.getSelectionBackground());
	}

	@Test
	public void testParseWithCustomHighlightColors() throws IOException {
		String schemaContent = """
				<?xml version="1.0" encoding="UTF-8"?>
				<schemalist>
				  <schema id="org.gnome.Terminal.Legacy.Profile">
				    <key name="palette" type="as">
				      <default>['rgb(0,0,0)', 'rgb(205,0,0)', 'rgb(0,205,0)', 'rgb(205,205,0)', 'rgb(0,0,238)', 'rgb(205,0,205)', 'rgb(0,205,205)', 'rgb(229,229,229)', 'rgb(127,127,127)', 'rgb(255,0,0)', 'rgb(0,255,0)', 'rgb(255,255,0)', 'rgb(92,92,255)', 'rgb(255,0,255)', 'rgb(0,255,255)', 'rgb(255,255,255)']</default>
				    </key>
				    <key name="foreground-color" type="s">
				      <default>'rgb(255,255,255)'</default>
				    </key>
				    <key name="background-color" type="s">
				      <default>'rgb(0,0,0)'</default>
				    </key>
				    <key name="highlight-colors-set" type="b">
				      <default>true</default>
				    </key>
				    <key name="highlight-foreground-color" type="s">
				      <default>'rgb(100,100,100)'</default>
				    </key>
				    <key name="highlight-background-color" type="s">
				      <default>'rgb(200,200,200)'</default>
				    </key>
				  </schema>
				</schemalist>
				""";

		File schemaFile = tempFolder.newFile("test-schema-custom-highlight.xml");
		Files.writeString(schemaFile.toPath(), schemaContent);

		Optional<GnomeTerminalColors> result = GnomeTerminalColorParser.parse(schemaFile.getAbsolutePath());

		assertTrue("Parser should successfully parse schema with custom highlight colors", result.isPresent());
		GnomeTerminalColors colors = result.get();

		// When highlight-colors-set is true, custom colors should be used
		assertEquals("Selection foreground should use custom color", new RGB(100, 100, 100),
				colors.getSelectionForeground());
		assertEquals("Selection background should use custom color", new RGB(200, 200, 200),
				colors.getSelectionBackground());
	}

	@Test
	public void testParseNonExistentFile() {
		Optional<GnomeTerminalColors> result = GnomeTerminalColorParser
				.parse("/nonexistent/path/to/schema.xml");
		assertFalse("Parser should return empty for non-existent file", result.isPresent());
	}

	@Test
	public void testParseInvalidXml() throws IOException {
		String invalidContent = "This is not valid XML";

		File schemaFile = tempFolder.newFile("invalid-schema.xml");
		Files.writeString(schemaFile.toPath(), invalidContent);

		Optional<GnomeTerminalColors> result = GnomeTerminalColorParser.parse(schemaFile.getAbsolutePath());

		assertFalse("Parser should return empty for invalid XML", result.isPresent());
	}

	@Test
	public void testParseIncompleteSchema() throws IOException {
		String incompleteContent = """
				<?xml version="1.0" encoding="UTF-8"?>
				<schemalist>
				  <schema id="org.gnome.Terminal.Legacy.Profile">
				    <key name="foreground-color" type="s">
				      <default>'rgb(255,255,255)'</default>
				    </key>
				  </schema>
				</schemalist>
				""";

		File schemaFile = tempFolder.newFile("incomplete-schema.xml");
		Files.writeString(schemaFile.toPath(), incompleteContent);

		Optional<GnomeTerminalColors> result = GnomeTerminalColorParser.parse(schemaFile.getAbsolutePath());

		assertFalse("Parser should return empty for incomplete schema (missing palette and background)",
				result.isPresent());
	}

	@Test
	public void testParsePaletteWithFewerColors() throws IOException {
		String schemaContent = """
				<?xml version="1.0" encoding="UTF-8"?>
				<schemalist>
				  <schema id="org.gnome.Terminal.Legacy.Profile">
				    <key name="palette" type="as">
				      <default>['rgb(0,0,0)', 'rgb(255,0,0)']</default>
				    </key>
				    <key name="foreground-color" type="s">
				      <default>'rgb(255,255,255)'</default>
				    </key>
				    <key name="background-color" type="s">
				      <default>'rgb(0,0,0)'</default>
				    </key>
				  </schema>
				</schemalist>
				""";

		File schemaFile = tempFolder.newFile("short-palette-schema.xml");
		Files.writeString(schemaFile.toPath(), schemaContent);

		Optional<GnomeTerminalColors> result = GnomeTerminalColorParser.parse(schemaFile.getAbsolutePath());

		assertFalse("Parser should return empty for palette with fewer than 16 colors", result.isPresent());
	}
}
