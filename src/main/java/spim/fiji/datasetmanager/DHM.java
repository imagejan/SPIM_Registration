package spim.fiji.datasetmanager;

import java.io.File;
import java.util.ArrayList;

import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.Angle;
import mpicbg.spim.data.sequence.Channel;
import mpicbg.spim.data.sequence.FinalVoxelDimensions;
import mpicbg.spim.data.sequence.Illumination;
import mpicbg.spim.data.sequence.ImgLoader;
import mpicbg.spim.data.sequence.MissingViews;
import mpicbg.spim.data.sequence.SequenceDescription;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewSetup;
import mpicbg.spim.data.sequence.VoxelDimensions;
import mpicbg.spim.io.IOFunctions;
import net.imglib2.Dimensions;
import net.imglib2.FinalDimensions;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import spim.fiji.spimdata.SpimData2;
import spim.fiji.spimdata.boundingbox.BoundingBoxes;
import spim.fiji.spimdata.imgloaders.DHMImgLoader;
import spim.fiji.spimdata.interestpoints.ViewInterestPoints;
import fiji.util.gui.GenericDialogPlus;

public class DHM implements MultiViewDatasetDefinition
{
	public static String defaultDir = "";
	public static double defaulCalX = 0.1725; //3.45 / 20.0;
	public static double defaulCalY = 0.1725; //3.45 / 20.0;
	public static double defaulCalZ = 12.5; //( 0.5 / ( 20.0 * 20.0 ) ) * 10000;
	public static String defaulCalUnit = "um";
	public static boolean defaultOpenAll = false;

	@Override
	public String getTitle()
	{
		return "Holographic Imaging Dataset";
	}

	@Override
	public String getExtendedDescription()
	{
		return
			"This dataset definition supports data as created by a holographic microscope\n" +
			"(Amplitude & Phase stacks in 3d over time)";
	}

	@Override
	public SpimData2 createDataset()
	{
		final DHMMetaData meta = queryDirectoryAndRatio();

		if ( meta == null )
			return null;

		if ( !meta.loadMetaData() )
			return null;

		// assemble timepints, viewsetups, missingviews and the imgloader
		final TimePoints timepoints = this.createTimePoints( meta );
		final ArrayList< ViewSetup > setups = this.createViewSetups( meta );
		final MissingViews missingViews = null;

		// instantiate the sequencedescription
		final SequenceDescription sequenceDescription =
				new SequenceDescription( timepoints, setups, null, missingViews );
		final ImgLoader< UnsignedShortType > imgLoader =
				new DHMImgLoader(
						meta.getDir(),
						meta.getStackDir(),
						meta.getAmplitudeDir(),
						meta.getPhaseDir(),
						meta.getTimepoints(),
						meta.getZPlanes(),
						meta.getExt(),
						meta.getAmpChannelId(),
						meta.getPhaseChannelId(),
						sequenceDescription );
		sequenceDescription.setImgLoader( imgLoader );

		// get the minimal resolution of all calibrations
		final double minResolution = Math.min( Math.min( meta.calX, meta.calY ), meta.calZ );

		IOFunctions.println( "Minimal resolution in all dimensions is: " + minResolution );
		IOFunctions.println( "(The smallest resolution in any dimension; the distance between two pixels in the output image will be that wide)" );
		
		// create the initial view registrations (they are all the identity transform)
		final ViewRegistrations viewRegistrations = StackList.createViewRegistrations( sequenceDescription.getViewDescriptions(), minResolution );
		
		// create the initial view interest point object
		final ViewInterestPoints viewInterestPoints = new ViewInterestPoints();
		viewInterestPoints.createViewInterestPoints( sequenceDescription.getViewDescriptions() );

		// finally create the SpimData itself based on the sequence description and the view registration
		final SpimData2 spimData = new SpimData2( meta.getDir(), sequenceDescription, viewRegistrations, viewInterestPoints, new BoundingBoxes() );

		return spimData;
	}

	/**
	 * Creates the {@link TimePoints} for the {@link SpimData} object
	 */
	protected TimePoints createTimePoints( final DHMMetaData meta )
	{
		final ArrayList< TimePoint > timepoints = new ArrayList< TimePoint >();

		for ( int t = 0; t < meta.getTimepoints().size(); ++t )
			timepoints.add( new TimePoint( Integer.parseInt( meta.getTimepoints().get( t ) ) ) );

		return new TimePoints( timepoints );
	}

	/**
	 * Creates the List of {@link ViewSetup} for the {@link SpimData} object.
	 * The {@link ViewSetup} are defined independent of the {@link TimePoint},
	 * each {@link TimePoint} should have the same {@link ViewSetup}s. The {@link MissingViews}
	 * class defines if some of them are missing for some of the {@link TimePoint}s
	 *
	 * @return
	 */
	protected ArrayList< ViewSetup > createViewSetups( final DHMMetaData meta )
	{
		final ArrayList< Channel > channels = new ArrayList< Channel >();
		channels.add( new Channel( meta.getAmpChannelId(), meta.getAmplitudeDir() ) );
		channels.add( new Channel( meta.getPhaseChannelId(), meta.getPhaseDir() ) );

		final ArrayList< Illumination > illuminations = new ArrayList< Illumination >();
		illuminations.add( new Illumination( 0, String.valueOf( 0 ) ) );

		final ArrayList< Angle > angles = new ArrayList< Angle >();
		angles.add( new Angle( 0, String.valueOf( 0 ) ) );

		final ArrayList< ViewSetup > viewSetups = new ArrayList< ViewSetup >();
		for ( final Channel c : channels )
			for ( final Illumination i : illuminations )
				for ( final Angle a : angles )
				{
					final VoxelDimensions voxelSize = new FinalVoxelDimensions( meta.calUnit, meta.calX, meta.calY, meta.calZ );
					final Dimensions dim = new FinalDimensions( new long[]{ meta.getWidth(), meta.getHeight(), meta.getDepth() } );
					viewSetups.add( new ViewSetup( viewSetups.size(), null, dim, voxelSize, c, a, i ) );
				}

		return viewSetups;
	}

	protected DHMMetaData queryDirectoryAndRatio()
	{
		GenericDialogPlus gd = new GenericDialogPlus( "Specify Holographic Acquistion Directory" );

		gd.addDirectoryField( "Holographic_Acquisition main directory", defaultDir, 50 );

		gd.addMessage( "" );
		gd.addMessage( "Camera pixel size (e.g. 3.45um) / Magnification (e.g. 20):" );
		gd.addNumericField( "Pixel_distance_x", defaulCalX, 5 );
		gd.addNumericField( "Pixel_distance_y", defaulCalY, 5 );
		gd.addMessage( "Depth between planes (e.g. 0.5cm) / Magnification^2 (e.g. 20^2) * 10000 (cm to um):" );
		gd.addNumericField( "Pixel_distance_z", defaulCalZ, 5 );
		gd.addStringField( "Pixel_unit", defaulCalUnit );
		gd.addMessage( "" );
		gd.addCheckbox( "Open_all planes to ensure they have the same dimensions (takes time!)", defaultOpenAll );
		gd.showDialog();
	
		if ( gd.wasCanceled() )
			return null;

		return new DHMMetaData(
				new File( defaultDir = gd.getNextString() ),
				defaulCalX = gd.getNextNumber(),
				defaulCalY = gd.getNextNumber(),
				defaulCalZ = gd.getNextNumber(),
				defaulCalUnit = gd.getNextString(),
				defaultOpenAll = gd.getNextBoolean() );
	}

	@Override
	public MultiViewDatasetDefinition newInstance()
	{
		return new DHM();
	}

	public static void main( String[] args )
	{
		new DHM().createDataset();
	}
}
