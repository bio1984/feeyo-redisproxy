package com.feeyo.redis.net.front.route;

import java.util.ArrayList;
import java.util.List;

import com.feeyo.redis.engine.codec.RedisRequest;
import com.feeyo.redis.engine.codec.RedisRequestPolicy;
import com.feeyo.redis.net.front.RedisFrontConnection;
import com.feeyo.redis.net.front.handler.CommandParse;
import com.feeyo.redis.net.front.prefix.KeyPrefixStrategy;
import com.feeyo.redis.net.front.prefix.KeyPrefixStrategyFactory;
import com.feeyo.redis.net.front.route.strategy.AbstractRouteStrategy;
import com.feeyo.redis.net.front.route.strategy.RouteStrategyFactory;

/**
 * 路由功能
 * @author yangtao
 *
 */
public class RouteService {
	
	// 路由计算, 必须认证后
	public static RouteResult route(List<RedisRequest> requests, RedisFrontConnection frontCon) 
			throws InvalidRequestExistsException, ManageRespNotTransException, AutoRespNotTransException, PhysicalNodeUnavailableException {
		
		int poolId = frontCon.getUserCfg().getPoolId();
		int poolType = frontCon.getUserCfg().getPoolType();
		boolean isReadOnly = frontCon.getUserCfg().isReadonly();
		boolean isAdmin = frontCon.getUserCfg().isAdmin();
		boolean isPipeline = requests.size() > 1;

		List<Integer> autoResponseIndexs = new ArrayList<Integer>();										// 直接返回指令索引
		
		// 请求是否存在不合法
		boolean invalidPolicyExist = false;
		
		// 是否需要分段
		boolean isNeedSegment = false;
		for(int i = 0; i < requests.size(); i++) {
			
			RedisRequest request = requests.get(i);
			if (request == null || request.getArgs().length == 0) {
				return null;
			}
			
			String cmd = new String( request.getArgs()[0] ).toUpperCase();
			RedisRequestPolicy policy = CommandParse.getPolicy( cmd );
			request.setPolicy( policy );
			
			// 包含批量操作命令，则采用分段的路由策略
			if(!isNeedSegment && ( 
					policy.getLevel() == CommandParse.MGETSET_CMD 
					|| (policy.getLevel() == CommandParse.DEL_CMD && request.getArgs().length > 2 )
					|| (policy.getLevel() == CommandParse.EXISTS_CMD && request.getArgs().length > 2) )) {
				isNeedSegment = true;
			}
			
			// 如果是管理指令，且非pipeline,且是管理员用户  提前跳出
			if ( !isPipeline && policy.getLevel() == CommandParse.MANAGE_CMD && isAdmin) {
				throw new ManageRespNotTransException("manage cmd exist", requests);
			}
			
			// 如果上个指令是合法的，继续校验下个指令
			if ( !invalidPolicyExist ) {
				invalidPolicyExist = RouteUtil.checkIsInvalidPolicy( poolType, policy, isReadOnly, isAdmin, isPipeline );
			}
					
			// 不需要透传，中间件自动回复
			if ( policy.getLevel() == CommandParse.AUTO_RESP_CMD || policy.getLevel() == CommandParse.MANAGE_CMD ) {
				autoResponseIndexs.add(i);
				continue;
			} 

			// 前缀构建 
			byte[] prefix = frontCon.getUserCfg().getPrefix();
			if (prefix != null) {
				KeyPrefixStrategy strategy = KeyPrefixStrategyFactory.getStrategy(cmd);
				strategy.rebuildKey(request, prefix);
			}
		}
		
		// 存在不支持指令
		if ( invalidPolicyExist ) {
			throw new InvalidRequestExistsException("invalid policy exist", requests);
		}
		
		// 全部自动回复
		if ( autoResponseIndexs.size() == requests.size() ) {
			throw new AutoRespNotTransException("auto response", requests);
		}
		
		// 根据请求做路由
		AbstractRouteStrategy routeStrategy = RouteStrategyFactory.getStrategy(poolType, isNeedSegment);
		RouteResult routeResult = routeStrategy.route(poolId, requests);
		routeResult.setAutoResponseIndexs( autoResponseIndexs );
		return routeResult;
	}

}