package com.feeyo.redis.net.front.route.strategy;

/**
 * route strategy factory
 *
 * @author Tr!bf wangyamin@variflight.com
 */
public class RouteStrategyFactory {
	
	private static DefaultRouteStrategy _DEFAULT = new DefaultRouteStrategy();
	private static SegmentRouteStrategy  _SEGMENT = new SegmentRouteStrategy();
	
    public static AbstractRouteStrategy getStrategy(int poolType, boolean isNeedSegment) {
    	// 集群情况下，需要对 Mset、Mget、Del mulitKey 分片
    	switch( poolType ) {
    	case 1:
    	case 2:
    		if ( isNeedSegment )
    			 return _SEGMENT;
    		break;
    	}
    	return _DEFAULT;
    }

}
