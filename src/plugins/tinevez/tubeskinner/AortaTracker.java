package plugins.tinevez.tubeskinner;

import java.awt.Color;
import java.awt.geom.Point2D;

import icy.image.IcyBufferedImage;
import icy.sequence.Sequence;
import icy.type.DataType;
import icy.type.collection.array.Array1DUtil;
import plugins.kernel.roi.roi2d.ROI2DEllipse;
import plugins.kernel.roi.roi2d.ROI2DPolyLine;
import plugins.kernel.roi.roi3d.ROI3DArea;

public class AortaTracker
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
	private final int segmentationChannel = 0;

	/**
	 * Time-point currently processed.
	 */
	private final int timepoint = 0;


	/**
	 * Search window for the local max of intensity along a radius.
	 */
	private final int windowRay = 15;

	public AortaTracker( final Sequence sequence, final ROI2DEllipse ellipse, final double thickness, final int window )
	{
		this.sequence = sequence;
		this.ellipse = ellipse;
		this.thickness = thickness;
		this.searchWindow = window;
	}

	public void run()
	{
		// Outer crown circle.
		final ROI2DEllipse roiOut = ellipse;
//		new ROI2DEllipse(
//				ellipse.getBounds2D().getMinX() - thickness / 2,
//				ellipse.getBounds2D().getMinY() - thickness / 2,
//				ellipse.getBounds2D().getMaxX() + thickness / 2,
//				ellipse.getBounds2D().getMaxY() + thickness / 2 );

		// Inner crown circle.
		final ROI2DEllipse roiIn = new ROI2DEllipse(
				roiOut.getBounds2D().getMinX() + thickness,
				roiOut.getBounds2D().getMinY() + thickness,
				roiOut.getBounds2D().getMaxX() - thickness,
				roiOut.getBounds2D().getMaxY() - thickness );

		ROI2DEllipse innerPrevious = roiIn;
		ROI2DEllipse outerPrevious = roiOut;

		final Sequence outWrap = new Sequence( "outWrap" );
		final IcyBufferedImage unWrapImage = new IcyBufferedImage( 360, sequence.getSizeZ(), 1, DataType.DOUBLE );
		outWrap.addImage( unWrapImage );

		final int width = sequence.getWidth();
		final int height = sequence.getHeight();
		
		unWrapImage.beginUpdate();

		final ROI3DArea skin = new ROI3DArea();
		skin.setName( "tmp skin" );

		final ROI3DArea tube = new ROI3DArea();
		tube.setName( "tmp tube" );

		for ( int z = 1; z < sequence.getSizeZ(); z++ )
		{
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

					/*
					 * TODO We actually do not sum over the whole crown but
					 * simply along a circle. Use ImgLib2 Iterators?
					 * 
					 * TODO Add bound checks.
					 */

					for ( float angle = 0; angle < 2 * 3.14d; angle += 0.1 )
					{
						final int xx = ( int ) ( xOffset + center.getX() + Math.cos( angle ) * rayOuter );
						final int yy = ( int ) ( yOffset + center.getY() + Math.sin( angle ) * rayOuter );
						val += data[ yy * image.getWidth() + xx ];
					}

					for ( float angle = 0; angle < 2 * 3.14d; angle += 0.1 )
					{
						final int xx = ( int ) ( xOffset + center.getX() + Math.cos( angle ) * rayInner );
						final int yy = ( int ) ( yOffset + center.getY() + Math.sin( angle ) * rayInner );
						val -= data[ yy * image.getWidth() + xx ];
					}

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
//			final Rectangle2D in = inner.getBounds2D();
//			final Rectangle2D out = outer.getBounds2D();
//			new ROI2DEllipse(
//					( in.getMinX() + out.getMinX() ) / 2,
//					( in.getMinY() + out.getMinY() ) / 2,
//					( in.getMaxX() + out.getMaxX() ) / 2,
//					( in.getMaxY() + out.getMaxY() ) / 2 );
			tube.setSlice( z, tubeSection, false );

			innerPrevious = inner;
			outerPrevious = outer;

			/*
			 * Read max value along the rays emerging from the crown center, for
			 * all theta.
			 */

			ROI2DPolyLine maxFitROI = null;

			double previousBestRay = -1.;
			for ( int angle = 0; angle < 360; angle++ )
			{
				final Point2D center = new Point2D.Double( outer.getBounds2D().getCenterX(), outer.getBounds().getCenterY() );
				final double rayOuter = outer.getBounds2D().getWidth() / 2;


				double bestRay = 0;
				double valMax = java.lang.Double.NEGATIVE_INFINITY;

				for ( double ray = rayOuter - windowRay; ray < rayOuter + windowRay; ray++ )
				{
					final int xx = ( int ) ( center.getX() + Math.cos( Math.toRadians( angle ) ) * ray );
					final int yy = ( int ) ( center.getY() + Math.sin( Math.toRadians( angle ) ) * ray );

					if ( xx < 0 || yy < 0 || xx >= width || yy >= height )
						continue;
					

					// Weight value by its distance to the previous max found.
					double currentVal = data[ yy * image.getWidth() + xx ];
					if ( previousBestRay > 0 )
					{
						final double alpha = ( ray - previousBestRay ) / windowRay;
						currentVal = currentVal / ( 1 + alpha * alpha );
					}

					if ( currentVal > valMax )
					{
						valMax = currentVal;
						bestRay = ray;
					}

				}

				if ( previousBestRay < 0 )
					previousBestRay = bestRay;

				/*
				 * TODO Do not use theta for the X axis of the unwrapped image,
				 * but instead the curvilinear coordinate along the contour.
				 */
				unWrapImage.setData( angle, z, 0, valMax );


				// Build polygon contour.
				final Point2D point = new Point2D.Double(
						center.getX() + Math.cos( Math.toRadians( angle ) ) * bestRay,
						center.getY() + Math.sin( Math.toRadians( angle ) ) * bestRay );

				if ( maxFitROI == null )
					maxFitROI = new ROI2DPolyLine( point );
				else
					maxFitROI.addNewPoint( point, false );
			}

			// Add this Z-slice contour to the 3D ROI.
			maxFitROI.setZ( z );
			skin.setSlice( z, maxFitROI, false );
		}

		skin.setColor( Color.CYAN );
		sequence.addROI( skin );

		tube.setColor( Color.ORANGE );
		sequence.addROI( tube );

		unWrapImage.endUpdate();

	}

}
