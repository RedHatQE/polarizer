(defproject com.github.redhatqe/polarizer-polarizer "0.3.1-SNAPSHOT"
  :description "Polarizer base for RHSM TestNG"
  :url "https://github.com/RedHatQE/polarizer"
  :license {:name "GPL-3.0"
            :comment "GNU General Public License v3.0"
            :url "https://choosealicense.com/licenses/gpl-3.0"
            :year 2024
            :key "gpl-3.0"}
  :java-source-path "src"
  :java-source-paths ["src"]
  :javac-options {:debug "on"}
  :dependencies [
        [org.testng/testng "6.8.21"]
	[io.reactivex.rxjava2/rxjava "2.1.6"]
    	[io.vertx/vertx-core "3.5.0"]
	[com.fasterxml.jackson.dataformat/jackson-dataformat-yaml "2.9.2"]
	[org.apache.activemq/activemq-all "5.15.2"]
    	[org.apache.commons/commons-collections4 "4.1"]
        [org.apache.httpcomponents/httpclient "4.5.2"]
	;;remove the next 3 when vertx support multipart file uploads
    	[org.apache.httpcomponents/httpmime  "4.5.2"]
    	[org.apache.httpcomponents/httpasyncclient  "4.1.3"]
    	[com.mashape.unirest/unirest-java  "1.4.9"]
    	[com.google.code.gson/gson "2.6.2"]
    	[com.github.redhatqe/polarize-metadata   "0.1.1-SNAPSHOT"]
    	[com.github.redhatqe/polarizer-reporter "0.3.0-SNAPSHOT"]
    	[com.github.redhatqe/polarizer-umb      "0.3.0-SNAPSHOT"]
        [com.github.redhatqe/polarize           "0.8.4-SNAPSHOT"]
  ]
  :plugins [[lein2-eclipse "2.0.0"]])
