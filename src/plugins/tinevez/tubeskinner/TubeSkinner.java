package plugins.tinevez.tubeskinner;

import icy.gui.dialog.MessageDialog;
import icy.roi.ROI;
import icy.sequence.Sequence;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzVarInteger;
import plugins.kernel.roi.roi2d.ROI2DEllipse;

public class TubeSkinner extends EzPlug
{

	private final EzVarInteger crownThickness = new EzVarInteger( "Crown thickness", 15, 1, 1000, 1 );

	private final EzVarInteger searchWindow = new EzVarInteger( "Tube center search window", 5, 1, 1000, 1 );

	@Override
	public void clean()
	{}

	@Override
	protected void execute()
	{
		// Get current active image.
		final Sequence sequence = getActiveSequence();
		if ( null == sequence )
		{
			MessageDialog.showDialog( "Please select an image first.", MessageDialog.INFORMATION_MESSAGE );
			return;
		}

		// clean of previous ROIs
		for ( int i = sequence.getROIs().size() - 1; i >= 0; i-- )
		{
			final ROI roi = sequence.getROIs().get( i );
			if ( roi.getName().contains( "tmp" ) )
				sequence.removeROI( roi );
		}

		// tracking.
		final ROI2DEllipse ellipse;
		try
		{
			ellipse = ( ROI2DEllipse ) sequence.getROI2Ds().get( 0 );
		}
		catch ( final Exception e )
		{
			MessageDialog.showDialog( "Plase adjust a ROI Ellipse on the first slice of the stack." );
			return;
		}

		new AortaTracker( sequence, ellipse, crownThickness.getValue( true ).intValue(), searchWindow.getValue( true ).intValue() ).run();
	}

	@Override
	protected void initialize()
	{
		addEzComponent( crownThickness );
		addEzComponent( searchWindow );
	}

}
