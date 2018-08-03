package plugins.tinevez.tubeskinner;

import java.lang.reflect.InvocationTargetException;

import javax.swing.SwingUtilities;

import icy.file.Loader;
import icy.gui.viewer.Viewer;
import icy.main.Icy;
import icy.sequence.Sequence;
import plugins.kernel.roi.roi2d.ROI2DEllipse;

public class TestDrive
{

	public static void main( final String[] args ) throws InvocationTargetException, InterruptedException
	{
		final String imagePath = "samples/MyleneAorta.tif";

		Icy.main( args );
		final Sequence sequence = Loader.loadSequence( imagePath, 0, true );
		SwingUtilities.invokeAndWait( () -> {
			final Viewer viewer = new Viewer( sequence );
			viewer.setVisible( true );
			viewer.setPositionZ( 0 );
		} );

		sequence.removeAllROI();
		final ROI2DEllipse circle = new ROI2DEllipse( 63, 46, 147, 130 );
		final int segmentationChannel = 0;
		final double thickness = 15.;
		final int window = 5;
		final boolean processAllTimePoints = false;
		final double thetaStart = 45.;
		final TubeSkinner tubeSkinner = new TubeSkinner( sequence, circle, segmentationChannel, thickness, window, processAllTimePoints, thetaStart );
		tubeSkinner.run();

	}

	private TestDrive()
	{}

}
