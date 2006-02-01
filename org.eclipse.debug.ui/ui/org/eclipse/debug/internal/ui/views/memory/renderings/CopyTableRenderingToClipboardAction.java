/*******************************************************************************
 * Copyright (c) 2004, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/


package org.eclipse.debug.internal.ui.views.memory.renderings;

import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IMemoryBlock;
import org.eclipse.debug.core.model.IMemoryBlockExtension;
import org.eclipse.debug.internal.ui.DebugPluginImages;
import org.eclipse.debug.internal.ui.DebugUIMessages;
import org.eclipse.debug.internal.ui.IInternalDebugUIConstants;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;


/**
 * Toobar Copy View Tab to Clipboard action
 * 
 * @since 3.0
 */
public class CopyTableRenderingToClipboardAction extends Action
{
	private final String COLUMN_SEPERATOR = "  "; //$NON-NLS-1$
	
	private AbstractBaseTableRendering fRendering;
	private StructuredViewer fViewer;
	
	public CopyTableRenderingToClipboardAction(AbstractBaseTableRendering rendering, StructuredViewer viewer)
	{
		super();
		fRendering = rendering;
		fViewer = viewer;
		setText(DebugUIMessages.CopyViewToClipboardAction_title);
		setToolTipText(DebugUIMessages.CopyViewToClipboardAction_tooltip);
		setImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_ELCL_COPY_VIEW_TO_CLIPBOARD));
		setHoverImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_LCL_COPY_VIEW_TO_CLIPBOARD));
		setDisabledImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_DLCL_COPY_VIEW_TO_CLIPBOARD));
	}

	private String concatenateTableAsString(TableItem[] itemList) {
		if (itemList.length == 0) return null;

		StringBuffer tableContents = new StringBuffer();
		
		Table table = (Table)fViewer.getControl();
		int numColumns = table.getColumnCount();
		ITableLabelProvider labelProvider = (ITableLabelProvider)fViewer.getLabelProvider();		
		TableColumn columns[] = table.getColumns();
		
		// get title of view tab
		String label = fRendering.getLabel();
		tableContents.append(label);
		tableContents.append(System.getProperty("line.separator")); //$NON-NLS-1$
		tableContents.append(COLUMN_SEPERATOR);
		
		int charsPerByte = fRendering.getNumCharsPerByte();
		if (charsPerByte < 0)
			charsPerByte = 4;
		
		//get the column headers and line them up properly
		for (int k=0; k < numColumns; k++) {
			
			StringBuffer columnLabel = new StringBuffer(columns[k].getText());
			int numBytes = 0;
			int numChars = 0;
			
			if (k > 0)
			{	
				numBytes = fRendering.getBytesPerColumn();
				numChars = numBytes * charsPerByte;
			}
			else
			{
				// special for address column
				IMemoryBlock memBlock = fRendering.getMemoryBlock();
				if (memBlock instanceof IMemoryBlockExtension)
				{
					try {
						numBytes = ((IMemoryBlockExtension)memBlock).getAddressSize();
					} catch (DebugException e) {
						numBytes = 0;
					}
					
					// check address size
					if (numBytes <= 0)
						numBytes = 4;
				}
				else
				{
					numBytes = 4;
				}
				numChars = numBytes*2;
				
			}
			
			 while (columnLabel.length() < numChars)
			 {
				 columnLabel.append(" "); //$NON-NLS-1$
			 }
				
			tableContents.append(columnLabel);	
			tableContents.append(COLUMN_SEPERATOR);
		}
		
		tableContents.append(System.getProperty("line.separator")); //$NON-NLS-1$
		StringBuffer temp;
			
		//get the column contents from all the rows
		for (int i=0; i < itemList.length; i++) {
			for (int j=0; j < numColumns; j++) {
				tableContents.append(COLUMN_SEPERATOR);
				
				temp = new StringBuffer(labelProvider.getColumnText(itemList[i].getData(), j));
				
				if (j>0)
				{	
					int numBytes = fRendering.getBytesPerColumn();
					int numChars = numBytes * charsPerByte;
					while (temp.length() < numChars)
					{
						temp.append(" "); //$NON-NLS-1$
					}
				}
				
				tableContents.append(temp);
			}
			tableContents.append(System.getProperty("line.separator")); //$NON-NLS-1$
		}
		return tableContents.toString();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.action.IAction#run()
	 */
	public void run() {
		
		if (fRendering == null)
			return;
		
		if (! (fViewer.getControl() instanceof Table))
			return;
		
		Table table = (Table)fViewer.getControl();
		
		if (table == null)
			return;
		Clipboard clip= null;
		try {
			clip = new Clipboard(table.getDisplay());
			TableItem[] tableItems = table.getItems();
			String tableAsString = new String();
			tableAsString = concatenateTableAsString(tableItems);
			if (!tableAsString.equals("")) { //$NON-NLS-1$
				TextTransfer plainTextTransfer = TextTransfer.getInstance();
				clip.setContents(new Object[] {tableAsString}, new Transfer[] {plainTextTransfer});
			}
		} finally {
			if (clip != null) {
				clip.dispose();
			}
		}
	}
}
