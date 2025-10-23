## Clone the Git repository of the Java project you want to use as a dependency into a local directory on your machine. This directory can be within your main project's workspace or a separate location.

    git clone <repository_url> <local_path_to_dependency>


## Include as a Subproject (Optional but Recommended for Local Development). In settings.gradle of your main project:

    include ':your-dependency-project-name'
    project(':your-dependency-project-name').projectDir = file('<relative_path_to_dependency_project>')


## In build.gradle of your main project, add the dependency in the dependencies block:

    dependencies {
        implementation project(':your-dependency-project-name')
    }

