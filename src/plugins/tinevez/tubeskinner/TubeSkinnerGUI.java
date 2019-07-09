package plugins.tinevez.tubeskinner;

import icy.gui.dialog.MessageDialog;
import icy.gui.viewer.Viewer;
import icy.roi.ROI;
import icy.sequence.Sequence;
import plugins.adufour.blocks.lang.Block;
import plugins.adufour.blocks.util.VarList;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzStoppable;
import plugins.adufour.ezplug.EzVarBoolean;
import plugins.adufour.ezplug.EzVarDouble;
import plugins.adufour.ezplug.EzVarInteger;
import plugins.adufour.ezplug.EzVarSequence;
import plugins.adufour.vars.lang.VarSequence;
import plugins.kernel.roi.roi2d.ROI2DEllipse;

public class TubeSkinnerGUI extends EzPlug implements EzStoppable, Block
{

	private static final String PLUGIN_VERSION = "1.1.1";

	private static final String PLUGIN_NAME = "TubeSkinner v" + PLUGIN_VERSION;

	private final EzVarInteger segmentationChannel = new EzVarInteger( "Segmentation channel", 0, 0, 10, 1 );

	private final EzVarInteger crownThickness = new EzVarInteger( "Crown thickness", 15, 1, 1000, 1 );

	private final EzVarInteger searchWindow = new EzVarInteger( "Tube center search window", 5, 0, 1000, 1 );

	private final EzVarBoolean allTimePoints = new EzVarBoolean( "Process all time-points", false );

	private final EzVarDouble thetaStart = new EzVarDouble( "Start at theta = ", 0., -360., 360., 45. );

	private final EzVarInteger thetaRange = new EzVarInteger( "Evaluate theta over = ", 360, 90, 360, 45 );

	private TubeSkinner aortaTracker;

	private final VarSequence outWrap = new VarSequence( "Unwrapped image", ( Sequence ) null );

	private final EzVarSequence inImage = new EzVarSequence( "Input image" );

	@Override
	public void clean()
	{}

	@Override
	public void stopExecution()
	{
		if ( aortaTracker != null )
			aortaTracker.cancel();
	}

	@Override
	protected void execute()
	{
		final Sequence sequence;

		if ( this.isHeadLess() )
		{
			sequence = inImage.getValue();
		}

		else
		{
			// Get current active image.
			final Viewer viewer = getActiveViewer();
			if ( null == viewer )
			{
				MessageDialog.showDialog( "Please select an image first.", MessageDialog.INFORMATION_MESSAGE );
				return;
			}
			sequence = viewer.getSequence();
			if ( null == sequence )
			{
				MessageDialog.showDialog( "Please select an image first.", MessageDialog.INFORMATION_MESSAGE );
				return;
			}
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
		final int currentTimePoint;
		try
		{
			ellipse = ( ROI2DEllipse ) sequence.getROI2Ds().get( 0 );
		}
		catch ( final Exception e )
		{
			MessageDialog.showDialog( "Plase adjust a ROI Ellipse on the first slice of the stack." );
			return;
		}

		if ( ellipse.getT() == -1 ) // ROI is not bound a specific timepoint
		{
			currentTimePoint = 0; // Default is first time point
		}
		else // ROI is specifying a specific timepoint
		{
			currentTimePoint = ellipse.getT();
		}

		this.aortaTracker = new TubeSkinner(
				sequence,
				ellipse,
				segmentationChannel.getValue( true ).intValue(),
				crownThickness.getValue( true ).intValue(),
				searchWindow.getValue( true ).intValue(),
				allTimePoints.getValue( true ),
				thetaStart.getValue( true ).doubleValue(),
				thetaRange.getValue( true ).intValue() );
		aortaTracker.setTimePoint( currentTimePoint );
		outWrap.setValue( aortaTracker.run( this.isHeadLess() ) );

	}

	@Override
	protected void initialize()
	{
		addEzComponent( inImage );
		addEzComponent( segmentationChannel );
		addEzComponent( crownThickness );
		addEzComponent( searchWindow );
		addEzComponent( allTimePoints );
		addEzComponent( thetaStart );
		addEzComponent( thetaRange );
	}

	@Override
	public String getName()
	{
		return PLUGIN_NAME;
	}

	@Override
	public void declareInput( final VarList inputMap )
	{
		inputMap.add( "Input image", this.inImage.getVariable() );
		inputMap.add( "Segmentation channel", this.segmentationChannel.getVariable() );
		inputMap.add( "Crown thickness", this.crownThickness.getVariable() );
		inputMap.add( "Tube center search window", this.searchWindow.getVariable() );
		inputMap.add( "Process all time-points", this.allTimePoints.getVariable() );
		inputMap.add( "Start at theta = ", this.thetaStart.getVariable() );
		inputMap.add( "Evaluate theta over = ", this.thetaRange.getVariable() );
	}

	@Override
	public void declareOutput( final VarList outputMap )
	{
		outputMap.add( "Unwrapped image", this.outWrap );
	}

}
