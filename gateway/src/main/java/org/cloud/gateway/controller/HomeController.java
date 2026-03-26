package org.cloud.gateway.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Flux;

@Controller
public class HomeController {

    private final RouteDefinitionLocator routeDefinitionLocator;

    @Autowired
    public HomeController(RouteDefinitionLocator routeDefinitionLocator) {
        this.routeDefinitionLocator = routeDefinitionLocator;
    }

    /**
     * 网关的首页
     * @apiNote 显示网关的一些路由信息
     * @param model 包含一些路由属性的模型
     * @return 首页文件
     */
    @GetMapping("/")
    public String home(Model model) {
        Flux<RouteDefinition> routes = routeDefinitionLocator.getRouteDefinitions();
        model.addAttribute("routes", routes);
        return "index";
    }
}
