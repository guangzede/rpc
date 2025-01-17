package com.boke.rpc.bean;

import com.boke.rpc.handler.ServiceHandler;
import com.boke.rpc.proxy.ProxyFactory;
import com.boke.rpc.regist.RegistCenter;
import com.boke.rpc.regist.zk.ZookeeperRegist;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BeanGetRegist implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware {

    public static List<String> nodes;
    private static ScheduledExecutorService scheduledThreadPool = Executors.newScheduledThreadPool(1);

    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        if (nodes != null) {
            Set<Class<?>> beanClazzs = null;
            try {
                beanClazzs = getBeanClass();
            } catch (Exception e) {
                e.printStackTrace();
            }
            for (Class beanClazz : beanClazzs) {
                BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(beanClazz);
                GenericBeanDefinition definition = (GenericBeanDefinition) builder.getRawBeanDefinition();

                //在这里，我们可以给该对象的属性注入对应的实例。
                //比如mybatis，就在这里注入了dataSource和sqlSessionFactory，
                // 注意，如果采用definition.getPropertyValues()方式的话，
                // 类似definition.getPropertyValues().add("interfaceType", beanClazz);
                // 则要求在FactoryBean（本应用中即ServiceFactory）提供setter方法，否则会注入失败
                // 如果采用definition.getConstructorArgumentValues()，
                // 则FactoryBean中需要提供包含该属性的构造方法，否则会注入失败
                definition.getConstructorArgumentValues().addGenericArgumentValue(beanClazz);

                //注意，这里的BeanClass是生成Bean实例的工厂，不是Bean本身。
                // FactoryBean是一种特殊的Bean，其返回的对象不是指定类的一个实例，
                // 其返回的是该工厂Bean的getObject方法所返回的对象。
                definition.setBeanClass(ProxyFactory.class);

                //这里采用的是byType方式注入，类似的还有byName等
                definition.setAutowireMode(GenericBeanDefinition.AUTOWIRE_BY_TYPE);
                registry.registerBeanDefinition(beanClazz.getSimpleName(), definition);
            }
        } else {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.err.println("获取消费者列表失败");
            postProcessBeanDefinitionRegistry(registry);
        }
    }

    private ApplicationContext applicationContext;

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public void postProcessBeanFactory(ConfigurableListableBeanFactory configurableListableBeanFactory) throws BeansException {

    }

    public static void init(String rpc_regist_address) throws IOException {
        scheduledThreadPool.scheduleAtFixedRate(new Task(rpc_regist_address), 0, 10, TimeUnit.SECONDS);
        scheduledThreadPool.execute(new Task(rpc_regist_address));
    }

    static class Task implements Runnable {
        private String rpc_regist_address;

        public Task(String rpc_regist_address) {
            this.rpc_regist_address = rpc_regist_address;
        }

        public void run() {
            System.out.println("注册中心地址为：" + rpc_regist_address);
            RegistCenter registCenter = new ZookeeperRegist(rpc_regist_address);
            nodes = registCenter.getChildren();
            if (nodes != null) {
                BeanGetRegist.nodes = nodes;
                System.out.println("获取节点成功" + nodes);
                try {
                    getBeanClass();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println("获取节点为空");
            }
        }
    }

    public static Set<Class<?>> getBeanClass() throws Exception {
        if (nodes != null) {
            HashMap<String, List<String>> map = new HashMap<>();
            Set<Class<?>> beanClazzs = new HashSet<Class<?>>();
            Set<String> classNames = new HashSet<String>();
            for (String node : nodes) {
                classNames.add(node.split("#")[0]);
            }
            for (String className : classNames) {
                List<String> list = new ArrayList<>();
                for (String node : nodes) {
                    if (node.split("#")[0].equals(className)) {
                        list.add(node.split("#")[1]);
                    }
                }
                try {
                    beanClazzs.add(Class.forName(className));
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
                map.put(className, list);
            }
            ServiceHandler.map = map;
            return beanClazzs;
        } else {
            throw new Exception();
        }
    }
}
