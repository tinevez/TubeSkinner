package plugins.tinevez.tubeskinner;

import java.lang.reflect.InvocationTargetException;

import javax.swing.SwingUtilities;

import icy.file.Loader;
import icy.gui.viewer.Viewer;
import icy.main.Icy;
import icy.plugin.PluginLauncher;
import icy.plugin.PluginLoader;
import icy.sequence.Sequence;
import plugins.kernel.roi.roi2d.ROI2DEllipse;

public class GUIExample
{

	public static void main( final String[] args ) throws InvocationTargetException, InterruptedException
	{
		final String imagePath = "/media/sherbert/Data/Scripts/eclipse-workspace/TubeSkinner/samples/MyleneAorta.tif";

		Icy.main( args );
		final Sequence sequence = Loader.loadSequence( imagePath, 0, true );
		SwingUtilities.invokeLater( () -> {
			final Viewer viewer = new Viewer( sequence );
			viewer.setVisible( true );
			viewer.setPositionZ( 0 );
			sequence.removeAllROI();
			final ROI2DEllipse circle = new ROI2DEllipse( 63, 46, 147, 130 );
			sequence.addROI( circle );
			PluginLauncher.start( PluginLoader.getPlugin( TubeSkinnerGUI.class.getName() ) );
			// new TubeSkinnerGUI().showUI();
		} );
	}

	private GUIExample()
	{}
}
