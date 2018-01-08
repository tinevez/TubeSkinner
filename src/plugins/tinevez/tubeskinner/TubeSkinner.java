package plugins.tinevez.tubeskinner;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.lang.reflect.InvocationTargetException;

import javax.swing.SwingUtilities;

import icy.gui.viewer.Viewer;
import icy.image.IcyBufferedImage;
import icy.sequence.Sequence;
import icy.type.DataType;
import icy.type.collection.array.Array1DUtil;
import plugins.kernel.roi.roi2d.ROI2DEllipse;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;
import plugins.kernel.roi.roi3d.ROI3DArea;

public class TubeSkinner
{

	/**
	 * The sequence in which the tube is. Must be laid out roughly along the Z
	 * axis.
	 */
	private final Sequence sequence;

	/**
	 * The user provided ellipse contour of the tube on the first Z-slice.
	 */
	private final ROI2DEllipse ellipse;

	/** Size of the crown to localize tube center. */
	private final double thickness;

	/** By how much can the tube center moves from z-slice to z-slice. */
	private final int searchWindow;

	/**
	 * In what channel should we perform the segmentation step.
	 */
	private final int segmentationChannel;

	/**
	 * Search window for the local max of intensity along a radius.
	 */
	private final int windowRay = 15;

	/**
	 * How many angles are probed when for each section.
	 */
	private final int nAngles = 360;

	/**
	 * Whether we should process all time-points or just the current time-point.
	 */
	private final boolean processAllTimePoints;

	private int targetTimePoint;

	private final double thetaStart;

	private boolean canceled = false;;

	public TubeSkinner( final Sequence sequence, final ROI2DEllipse ellipse, final int segmentationChannel, final double thickness, final int window, final boolean processAllTimePoints, final double thetaStart )
	{
		this.sequence = sequence;
		this.ellipse = ellipse;
		this.segmentationChannel = segmentationChannel;
		this.thickness = thickness;
		this.searchWindow = window;
		this.processAllTimePoints = processAllTimePoints;
		this.thetaStart = thetaStart;
	}

	public void run()
	{
		canceled = false;
		final int nt = processAllTimePoints ? sequence.getSizeT() : 1;

		final Sequence outWrap = new Sequence( "Unwrapped " + sequence.getName() );
		outWrap.setPixelSizeY( sequence.getPixelSizeZ() );
		outWrap.setPixelSizeX( sequence.getPixelSizeZ() );
		outWrap.setTimeInterval( sequence.getTimeInterval() );

		try
		{
			SwingUtilities.invokeAndWait( new Runnable()
			{
				@Override
				public void run()
				{
					new Viewer( outWrap );
				}
			} );
		}
		catch ( final InvocationTargetException e )
		{
			e.printStackTrace();
		}
		catch ( final InterruptedException e )
		{
			e.printStackTrace();
		}

		if ( processAllTimePoints )
		{
			for ( int timepoint = 0; timepoint < nt; timepoint++ )
			{
				if ( canceled )
					return;
				processTimePoint( timepoint, outWrap );
			}
		}
		else
		{
			processTimePoint( targetTimePoint, outWrap );
		}

	}

	private void processTimePoint( final int timepoint, final Sequence outWrap )
	{
		final double pixelSize = 1.;
		final int nx = ( int ) ( Math.PI * ellipse.getBounds2D().getWidth() / pixelSize );
		final int nz = ( int ) ( sequence.getSizeZ() / pixelSize );
		final int nc = sequence.getSizeC();

		final IcyBufferedImage unWrapImage = new IcyBufferedImage( nx, nz, nc, DataType.FLOAT );
		outWrap.addImage( processAllTimePoints ? timepoint : 0, unWrapImage );

		final int width = sequence.getWidth();
		final int height = sequence.getHeight();

		unWrapImage.beginUpdate();

		final ROI3DArea skin = new ROI3DArea();
		skin.setName( "Skin_t=" + timepoint );
		skin.setT( timepoint );

		final ROI3DArea tube = new ROI3DArea();
		tube.setName( "RoughTube_t=" + timepoint );
		tube.setT( timepoint );

		// Outer crown circle.
		final ROI2DEllipse roiOut = ellipse;

		// Inner crown circle.
		final ROI2DEllipse roiIn = new ROI2DEllipse(
				roiOut.getBounds2D().getMinX() + thickness,
				roiOut.getBounds2D().getMinY() + thickness,
				roiOut.getBounds2D().getMaxX() - thickness,
				roiOut.getBounds2D().getMaxY() - thickness );

		ROI2DEllipse innerPrevious = roiIn;
		ROI2DEllipse outerPrevious = roiOut;

		for ( int z = 0; z < sequence.getSizeZ(); z++ )
		{
			if ( canceled )
				return;

			final int iy = ( int ) ( z / pixelSize );

			// Current crown from previous Z-slice.
			final ROI2DEllipse inner = ( ROI2DEllipse ) innerPrevious.getCopy();
			final ROI2DEllipse outer = ( ROI2DEllipse ) outerPrevious.getCopy();

			// Get pixel data as double (copy current slice).
			final IcyBufferedImage image = sequence.getImage( timepoint, z );
			final Object dataXY = image.getDataXY( segmentationChannel );
			final double[] data = Array1DUtil.arrayToDoubleArray( dataXY, image.isSignedDataType() );

			double currentMax = Integer.MIN_VALUE;
			Point2D bestOffset = null;
			for ( int xOffset = -searchWindow; xOffset <= searchWindow; xOffset++ )
			{
				for ( int yOffset = -searchWindow; yOffset <= searchWindow; yOffset++ )
				{
					double val = 0.;
					final Point2D center = new Point2D.Double( outer.getBounds2D().getCenterX(), outer.getBounds().getCenterY() );
					final double rayOuter = outer.getBounds2D().getWidth() / 2;
					final double rayInner = inner.getBounds2D().getWidth() / 2;

					double s = 0.;
					int n = 0;
					for ( float angle = 0; angle < 2 * 3.14d; angle += 0.1 )
					{
						final int xx = ( int ) ( xOffset + center.getX() + Math.cos( angle ) * rayOuter );
						final int yy = ( int ) ( yOffset + center.getY() + Math.sin( angle ) * rayOuter );
						if ( xx < 0 || yy < 0 || xx >= width || yy >= height )
							continue;
						s += data[ yy * image.getWidth() + xx ];
						n++;
					}
					val = s / n;

					s = 0.;
					n = 0;
					for ( float angle = 0; angle < 2 * 3.14d; angle += 0.1 )
					{
						final int xx = ( int ) ( xOffset + center.getX() + Math.cos( angle ) * rayInner );
						final int yy = ( int ) ( yOffset + center.getY() + Math.sin( angle ) * rayInner );
						if ( xx < 0 || yy < 0 || xx >= width || yy >= height )
							continue;
						s -= data[ yy * image.getWidth() + xx ];
						n++;
					}
					val += s / n;

					if ( val > currentMax )
					{
						currentMax = val;
						bestOffset = new Point2D.Double( xOffset, yOffset );
					}

				}
			}

			// shift roi.
			inner.translate( bestOffset.getX(), bestOffset.getY() );
			outer.translate( bestOffset.getX(), bestOffset.getY() );

			// display and manage rois.

			inner.setZ( z );
			outer.setZ( z );

			inner.setName( "tmp inner " + z );
			outer.setName( "tmp outer " + z );

			final ROI2DEllipse tubeSection = outer;
			tube.add( z, tubeSection.getBooleanMask( true ) );

			innerPrevious = inner;
			outerPrevious = outer;

			/*
			 * Read max value along the rays emerging from the crown center, for
			 * all theta.
			 */

			ROI2DPolyLine maxFitROI = null;
			double rPrev = -1.;
			double thetaPrev = -1.;

			final double R0 = outer.getBounds2D().getWidth() / 2;
			for ( int iTheta = 0; iTheta < nAngles; iTheta++ )
			{
				final double theta = 2 * Math.PI * ( ( double ) iTheta / nAngles + thetaStart / 360. );
				final Point2D center = new Point2D.Double( outer.getBounds2D().getCenterX(), outer.getBounds().getCenterY() );

				double rMax = R0;
				double intensityMax = java.lang.Double.NEGATIVE_INFINITY;

				final double[] values = new double[ nc ];
				for ( double r = R0 - windowRay; r < R0 + windowRay; r++ )
				{
					final long xx = Math.round( center.getX() + Math.cos( theta ) * r );
					final long yy = Math.round( center.getY() + Math.sin( theta ) * r );
					if ( xx < 0 || yy < 0 || xx >= width || yy >= height )
						continue;

					/*
					 * Weight value by its distance to the previous max found.
					 */
					double intensityR = data[ ( int ) ( yy * image.getWidth() + xx ) ];
					if ( rPrev > 0 )
					{
						final double alpha = ( r - rPrev ) / windowRay;
						intensityR = intensityR / ( 1 + alpha * alpha );
					}

					if ( intensityR > intensityMax )
					{
						intensityMax = intensityR;
						rMax = r;
						for ( int c = 0; c < nc; c++ )
							values[ c ] = image.getData( ( int ) xx, ( int ) yy, c );
					}

				}

				if ( rPrev < 0 )
				{
					rPrev = rMax;
					thetaPrev = theta;
					for ( int c = 0; c < nc; c++ )
						unWrapImage.setData( 0, iy, c, values[ c ] );
				}
				else
				{
					// Projected on the initial circle.
					final int i0 = ( int ) Math.round( R0 * ( thetaPrev - thetaStart / 180. * Math.PI ) );
					final int i1 = ( int ) Math.round( R0 * ( theta - thetaStart / 180. * Math.PI ) );

					if ( i0 < nx && i1 <= nx )
						for ( int ix = i0; ix < i1; ix++ )
							for ( int c = 0; c < nc; c++ )
								unWrapImage.setData( ix, iy, c, values[ c ] );

					thetaPrev = theta;
					rPrev = rMax;
				}

				// Build polygon contour.
				final Point2D point = new Point2D.Double(
						center.getX() + Math.cos( theta ) * rMax,
						center.getY() + Math.sin( theta ) * rMax );

				if ( maxFitROI == null )
					maxFitROI = new ROI2DPolyLine( point );
				else
					maxFitROI.addNewPoint( point, false );
			}

			// Add this Z-slice contour to the 3D ROI.
			maxFitROI.setZ( z );
			skin.add( z, maxFitROI.getBooleanMask( true ) );

			// Update display every 100th line.
			if ( z % 100 == 0 )
			{
				unWrapImage.endUpdate();
				unWrapImage.beginUpdate();
			}
		}

		unWrapImage.endUpdate();

		skin.setColor( Color.CYAN );
		sequence.addROI( skin );

		tube.setColor( Color.ORANGE );
		sequence.addROI( tube );

	}

	public void setTimePoint( final int targetTimePoint )
	{
		this.targetTimePoint = targetTimePoint;
	}

	public void cancel()
	{
		canceled = true;
	}
}
