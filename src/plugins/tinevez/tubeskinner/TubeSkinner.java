package plugins.tinevez.tubeskinner;

import icy.gui.dialog.MessageDialog;
import icy.sequence.Sequence;
import plugins.adufour.ezplug.EzPlug;
import plugins.adufour.ezplug.EzStoppable;
import plugins.adufour.ezplug.EzVarDouble;

public class TubeSkinner extends EzPlug implements EzStoppable
{

	private final EzVarDouble crownThickness = new EzVarDouble( "Crown thickness", 15, 1, 1000, 1 );

	private boolean stopRequested = false;


	@Override
	public void clean()
	{}

	@Override
	protected void execute()
	{
		stopRequested = false;

		// Get current active image.
		final Sequence sequence = getActiveSequence();
		if ( null == sequence )
		{
			MessageDialog.showDialog( "Please select an image first.", MessageDialog.INFORMATION_MESSAGE );
			return;
		}

		new AortaTracker( sequence, crownThickness.getValue( true ).doubleValue() ).run();
	}

	@Override
	protected void initialize()
	{
		addEzComponent( crownThickness );
	}

	@Override
	public void stopExecution()
	{
		stopRequested = true;
	}

}
