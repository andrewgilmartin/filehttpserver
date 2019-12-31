FileHttpServer was originally written to support a series of [blog postings](https://www.calliopesounds.com/2019/12/a-maven-repository-manager.html) about 
moving an ancient Ant build to Maven. That series needed a simple HTTP server to 
act as a Maven repository manager.

FileHttpServer is a very simple file-only serving HTTP server. It can only GET 
and PUT files. There are no authorization or other safety limitations. To use,
build 

    mvn clean package

and then run

    java -jar target/filehttpserver-1.0.0.jar port directory concurrency

END
