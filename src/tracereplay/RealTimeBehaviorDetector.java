package tracereplay;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import utility.Log;
import utility.Trace;

public class RealTimeBehaviorDetector {

	public RealTimeBehaviorDetector() {
		
	}
	
	public List<Trace> projected_accelerometer = new ArrayList<Trace>();
	

	private List<Trace> window_accelerometer = new LinkedList<Trace>();
	private List<Trace> window_rotation_matrix = new LinkedList<Trace>();
	private List<Trace> fittingpoints = new ArrayList<Trace>();

	private RotationMatrix rm = new RotationMatrix();
	
	
	private Trace lastSmoothedAccelerometer = null;
	private Trace curProjectedAccelerometer = null;

	static final int kWindowSize = 10;
	static final double alpha = 0.15;
	
	
	public void processTrace(Trace trace) {
		String type = trace.type;
		if(type.equals(Trace.ACCELEROMETER)) {
						
			window_accelerometer.add(trace);
			if(window_accelerometer.size() > kWindowSize) {
				boolean steady = stopped(window_accelerometer);
				if(steady && rm.rm_set == false) {
					List<Trace> sub = PreProcess.extractSubList(window_rotation_matrix, window_accelerometer.get(0).time, window_accelerometer.get(kWindowSize - 1).time);
					Trace tmprm = PreProcess.getAverage(sub);
					if(tmprm !=null) {
						rm.rotation_matrix = tmprm.values;
						rm.setRotationMatrix(tmprm);
						rm.rm_set = true;
						Log.log("rotation matrix is set");
					}
				}
				window_accelerometer.remove(0);
			}
			if(rm.rm_set == false) {
				return;
			}
			
			if(lastSmoothedAccelerometer == null) {
				lastSmoothedAccelerometer = new Trace(3);
				lastSmoothedAccelerometer.copyTrace(trace);
			} else {
				for(int m = 0; m < 3; ++m) {
					trace.values[m] = alpha * trace.values[m] + (1.0 - alpha) * lastSmoothedAccelerometer.values[m];
					lastSmoothedAccelerometer.values[m] = trace.values[m];
				}
			}
						
			Trace ntr = rm.Rotate(trace);

			if(Math.sqrt(Math.pow(ntr.values[0], 2.0) + Math.pow(ntr.values[1], 2.0)) > 0.05 && rm.aligned == false) {
				if(fittingpoints.size() < 400) {
				   fittingpoints.add(ntr);
				} else {
					double slope = rm.curveFit(fittingpoints);
					rm.setUnitVector(fittingpoints, slope);
					
					rm.aligned = true;
					Log.log("rotation matrix is aligned");
				}
			}
			if(rm.all_set == true || rm.aligned == true) {
				curProjectedAccelerometer = new Trace(Trace.ACCELEROMETER, 3);
				curProjectedAccelerometer = rm.Alignment(ntr);
				projected_accelerometer.add(curProjectedAccelerometer);
			} else {
				curProjectedAccelerometer = ntr;
			}
			
			
		} else if(type.equals(Trace.ROTATION_MATRIX)) {
			window_rotation_matrix.add(trace);
			if(window_rotation_matrix.size() > kWindowSize) {
				window_rotation_matrix.remove(0);
			}
		} else {
			Log.log("Uncaptured trace type", trace.toString());
		}
	}
	
	public boolean stopped(List<Trace> window) {
		double [] maxs = {-100.0, -100.0, -100.0};
		double [] mins = {100.0, 100.0, 100.0};		
		final int dim = 3;
		final double threshold = 0.15;
		for(int i = 0; i < window.size(); ++i) {
			Trace cur = window.get(i);
			for(int j = 0; j < dim; ++j) {
				if(cur.values[j] > maxs[j]) {
					maxs[j] = cur.values[j];
				}
				if(cur.values[j] < mins[j]) {
					mins[j] = cur.values[j];
				}
			}
		}
		for(int i = 0; i < dim; ++i) {
			if(Math.abs(maxs[i] - mins[i]) > threshold) {
				return false;
			}
		}
		return true;
	}
	

}