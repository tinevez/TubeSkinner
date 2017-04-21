package plugins.tinevez.tubeskinner;

import icy.gui.dialog.MessageDialog;
import icy.gui.viewer.Viewer;
import icy.roi.ROI;
import icy.sequence.Sequence;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzVarBoolean;
import plugins.adufour.ezplug.EzVarInteger;
import plugins.kernel.roi.roi2d.ROI2DEllipse;

public class TubeSkinner extends EzPlug
{

	private final EzVarInteger segmentationChannel = new EzVarInteger( "Segmentation channel", 0, 0, 10, 1 );

	private final EzVarInteger crownThickness = new EzVarInteger( "Crown thickness", 15, 1, 1000, 1 );

	private final EzVarInteger searchWindow = new EzVarInteger( "Tube center search window", 5, 1, 1000, 1 );

	private final EzVarBoolean allTimePoints = new EzVarBoolean( "Process all time-points", false );

	@Override
	public void clean()
	{}

	@Override
	protected void execute()
	{
		// Get current active image.
		final Viewer viewer = getActiveViewer();
		if ( null == viewer )
		{
			MessageDialog.showDialog( "Please select an image first.", MessageDialog.INFORMATION_MESSAGE );
			return;
		}
		final Sequence sequence = viewer.getSequence();
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

		final int currentTimePoint = viewer.getPositionT();

		final AortaTracker aortaTracker = new AortaTracker(
				sequence,
				ellipse,
				segmentationChannel.getValue( true ).intValue(),
				crownThickness.getValue( true ).intValue(),
				searchWindow.getValue( true ).intValue(),
				allTimePoints.getValue( true ) );
		aortaTracker.setTimePoint( currentTimePoint );
		aortaTracker.run();
	}

	@Override
	protected void initialize()
	{
		addEzComponent( segmentationChannel );
		addEzComponent( crownThickness );
		addEzComponent( searchWindow );
		addEzComponent( allTimePoints );
	}

}
