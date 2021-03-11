package org.swdc.plugin;

import io.github.classgraph.*;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;

import javax.inject.Inject;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 用来生成静态的AOP类。
 */
@Mojo(name = "gen-interceptor")
public class GenerateInterceptors extends AbstractMojo {

    @Inject
    private MavenProject project;

    @Inject
    private RepositorySystem system;

    private InvocationHandler handler = (object,method,param) -> null;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("Generate interceptor bytecode for graal");

        List<String> cp = new ArrayList<>();
        List<String> jarNames = new ArrayList<>();
        cp.add(project.getBuild().getOutputDirectory());

        ClassGraph graph = new ClassGraph();

        List<Artifact> artifacts = new ArrayList<>();

        for (Dependency dependency : project.getDependencies()) {
            Artifact artifact = system.createDependencyArtifact(dependency);
            ArtifactResolutionRequest resolutionRequest = new ArtifactResolutionRequest();
            resolutionRequest.setArtifact(artifact);
            ArtifactResolutionResult resolved = system.resolve(resolutionRequest);
            if (resolved.getArtifacts().size() > 0) {
                artifacts.addAll(resolved.getArtifacts());
            }
        }

        for (Artifact artifact: artifacts) {
            cp.add(artifact.getFile().getAbsolutePath());
            jarNames.add(artifact.getFile().getName());
        }

        ScanResult result = graph
                .enableAllInfo()
                .overrideClasspath(cp.toArray())
                .acceptJars(jarNames.toArray(String[]::new))
                .scan();

        Path output = Paths.get(project.getBuild().getOutputDirectory());

        // 加载并且解析被AOP的组件
        // ByteBuddy生成的类结构是这样的：一个运行时的InvocationHandler，以及static-final型的Method，
        // 因为Method是static-final的，所以应该在编译时就已经确定，使用的方法就是直接注入装配好的Invocation
        // Handler即可。
        ByteBuddy byteBuddy = new ByteBuddy();
        List<ClassInfo> infos = result.getClassesWithAnnotation("org.swdc.dependency.annotations.With");
        for (ClassInfo info: infos) {
            Class enhanceTarget = info.loadClass();
            DynamicType.Unloaded unloaded = byteBuddy.subclass(enhanceTarget)
                    .method(ElementMatchers.any())
                    .intercept(InvocationHandlerAdapter.of(handler))
                    .name(enhanceTarget.getName() + "$Proxied")
                    .make();
            // 字节码
            byte[] bytecode = unloaded.getBytes();
            String packageName = enhanceTarget.getPackageName();
            Path packageLocation = output.resolve(Paths.get(packageName.replace(".","/")));
            try {
                if (!Files.exists(packageLocation)) {
                    Files.createDirectories(packageLocation);
                }
                getLog().info("writing: " + enhanceTarget.getSimpleName() + "$Proxied.class");
                Files.write(packageLocation.resolve(enhanceTarget.getSimpleName() + "$Proxied.class"),bytecode);
            } catch (IOException e) {
                getLog().error(e);
            }
        }

    }
}
