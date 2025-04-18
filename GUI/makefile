CLASSPATH = .:mysql-connector-j-9.3.0.jar

all: clean compile run

clean:
	rm -f *.class

compile:
	javac -cp $(CLASSPATH) *.java

run:
	java -cp $(CLASSPATH) GUI

coverage:
	rm -r org*/ jacoco-resources default com*/ -f
	rm *.class *.html *.exec *.xml *.csv -f
	javac -cp $(CLASSPATH) *.java
	rm -Rf cov
	mkdir ./cov
	java -javaagent:jacocoagent.jar=excludes=org.jacoco.*,destfile=./cov/output.exec -cp $(CLASSPATH) GUI
	mv *.class ./cov
	java -jar ./jacococli.jar report ./cov/output.exec --classfiles ./cov --sourcefiles ./ --html ./cov/report