wd=$(pwd)
cd $1
find . -name "*.class" -type f -delete
find . -name "*.java" > sources.txt
javac @sources.txt
cd "$wd"