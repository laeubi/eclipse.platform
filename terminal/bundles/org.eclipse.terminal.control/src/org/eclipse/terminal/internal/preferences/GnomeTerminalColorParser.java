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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.swt.graphics.RGB;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Parser for GNOME Terminal color preferences from gschema.xml files.
 * 
 * @since 5.0
 */
public class GnomeTerminalColorParser {

	private static final String GNOME_TERMINAL_SCHEMA_PATH = "/usr/share/glib-2.0/schemas/org.gnome.Terminal.gschema.xml";
	private static final Pattern RGB_PATTERN = Pattern.compile("rgb\\((\\d+),(\\d+),(\\d+)\\)");

	/**
	 * Result of parsing GNOME Terminal color preferences.
	 */
	public static class GnomeTerminalColors {
		private final List<RGB> palette;
		private final RGB foreground;
		private final RGB background;
		private final RGB selectionForeground;
		private final RGB selectionBackground;

		public GnomeTerminalColors(List<RGB> palette, RGB foreground, RGB background, RGB selectionForeground,
				RGB selectionBackground) {
			this.palette = palette;
			this.foreground = foreground;
			this.background = background;
			this.selectionForeground = selectionForeground;
			this.selectionBackground = selectionBackground;
		}

		public List<RGB> getPalette() {
			return palette;
		}

		public RGB getForeground() {
			return foreground;
		}

		public RGB getBackground() {
			return background;
		}

		public RGB getSelectionForeground() {
			return selectionForeground;
		}

		public RGB getSelectionBackground() {
			return selectionBackground;
		}
	}

	/**
	 * Checks if the GNOME Terminal schema file exists.
	 * 
	 * @return true if the schema file exists
	 */
	public static boolean isAvailable() {
		Path schemaPath = Paths.get(GNOME_TERMINAL_SCHEMA_PATH);
		return Files.exists(schemaPath) && Files.isReadable(schemaPath);
	}

	/**
	 * Parses the GNOME Terminal color preferences.
	 * 
	 * @return Optional containing the parsed colors, or empty if parsing fails
	 */
	public static Optional<GnomeTerminalColors> parse() {
		return parse(GNOME_TERMINAL_SCHEMA_PATH);
	}

	/**
	 * Parses the GNOME Terminal color preferences from the specified file.
	 * 
	 * @param schemaPath path to the gschema.xml file
	 * @return Optional containing the parsed colors, or empty if parsing fails
	 */
	public static Optional<GnomeTerminalColors> parse(String schemaPath) {
		File schemaFile = new File(schemaPath);
		if (!schemaFile.exists() || !schemaFile.canRead()) {
			return Optional.empty();
		}

		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			factory.setXIncludeAware(false);
			factory.setExpandEntityReferences(false);

			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(schemaFile);
			document.getDocumentElement().normalize();

			// Find all key elements
			NodeList keyNodes = document.getElementsByTagName("key");

			List<RGB> palette = null;
			RGB foreground = null;
			RGB background = null;
			RGB highlightForeground = null;
			RGB highlightBackground = null;
			Boolean highlightColorsSet = null;

			// First pass: collect all values
			for (int i = 0; i < keyNodes.getLength(); i++) {
				Element keyElement = (Element) keyNodes.item(i);
				String keyName = keyElement.getAttribute("name");

				NodeList defaultNodes = keyElement.getElementsByTagName("default");
				if (defaultNodes.getLength() == 0) {
					continue;
				}

				String defaultValue = defaultNodes.item(0).getTextContent().trim();

				switch (keyName) {
				case "palette":
					palette = parsePalette(defaultValue);
					break;
				case "foreground-color":
					foreground = parseColor(defaultValue);
					break;
				case "background-color":
					background = parseColor(defaultValue);
					break;
				case "highlight-colors-set":
					highlightColorsSet = Boolean.parseBoolean(defaultValue);
					break;
				case "highlight-foreground-color":
					highlightForeground = parseColor(defaultValue);
					break;
				case "highlight-background-color":
					highlightBackground = parseColor(defaultValue);
					break;
				}
			}

			// Determine selection colors based on highlight-colors-set flag
			RGB selectionForeground;
			RGB selectionBackground;
			if (Boolean.TRUE.equals(highlightColorsSet) && highlightForeground != null && highlightBackground != null) {
				selectionForeground = highlightForeground;
				selectionBackground = highlightBackground;
			} else {
				// Default to swapped foreground/background
				selectionForeground = background != null ? background : new RGB(0, 0, 0);
				selectionBackground = foreground != null ? foreground : new RGB(255, 255, 255);
			}

			// Verify we have the minimum required data
			if (palette != null && palette.size() >= 16 && foreground != null && background != null) {
				return Optional.of(new GnomeTerminalColors(palette, foreground, background, selectionForeground,
						selectionBackground));
			}

		} catch (Exception e) {
			// Log error but don't throw - just return empty
			System.err.println("Failed to parse GNOME Terminal schema: " + e.getMessage());
		}

		return Optional.empty();
	}

	/**
	 * Parses a palette string from the gschema file.
	 * Expected format: ['rgb(r,g,b)', 'rgb(r,g,b)', ...]
	 * 
	 * @param paletteString the palette string
	 * @return list of RGB colors
	 */
	private static List<RGB> parsePalette(String paletteString) {
		List<RGB> colors = new ArrayList<>();
		Matcher matcher = RGB_PATTERN.matcher(paletteString);

		while (matcher.find()) {
			try {
				int r = Integer.parseInt(matcher.group(1));
				int g = Integer.parseInt(matcher.group(2));
				int b = Integer.parseInt(matcher.group(3));
				colors.add(new RGB(r, g, b));
			} catch (NumberFormatException e) {
				// Skip invalid color
			}
		}

		return colors;
	}

	/**
	 * Parses a single color string from the gschema file.
	 * Expected format: 'rgb(r,g,b)' or "rgb(r,g,b)"
	 * 
	 * @param colorString the color string
	 * @return RGB color or null if parsing fails
	 */
	private static RGB parseColor(String colorString) {
		Matcher matcher = RGB_PATTERN.matcher(colorString);
		if (matcher.find()) {
			try {
				int r = Integer.parseInt(matcher.group(1));
				int g = Integer.parseInt(matcher.group(2));
				int b = Integer.parseInt(matcher.group(3));
				return new RGB(r, g, b);
			} catch (NumberFormatException e) {
				return null;
			}
		}
		return null;
	}
}
