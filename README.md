# Cribl Log Collector

## Usage
In the main directory run `./mvnw spring-boot:run` to start application server.<br/>
`mvnw` is a Maven wrapper which is our build automation tool and dependency manager. The wrapper makes it easier so you don't need to install Maven on your system.

## Testing
Use a browser or terminal to test the REST WS. <br/>
Note, I am using basic http authentication to secure the WS. <br/>
UN: cribl PW: password
- WS via Web browser: <br/>
  - Head to http://localhost:8080/cribl/log/tail?filename=test.txt This will use the default test file I included with
the project.
  - In a browser you will be prompted for a username password. Enter it and view results in browser 
- WS via Terminal: <br/>
  - Use `curl -u cribl:password "http://localhost:8080/cribl/log/tail?filename=test.txt"`

## Design

To be discussed in later rounds of interviews but here was my thought process:

### File Reading

I used Java 8 IO Streams here which are part of the core JDK package.
They are very performant on large files (multiple gigabyte range) because they are lazily evaluated until the line is read.
I open the file with streams then reverse it, then read one line at a time until we hit the maximum requested log lines. <br/>
I was able to tail a 1.6 GB file in 6.8 seconds with this implementation.
<p/>
I hope this is ok, as I saw the notes in the assignment to not use external libraries for file reads. I proceeded with my solution bc it is a core language feature.<br/>
If you were looking to see this implemented at an even lower level I would take the following approach:<br/>

- Get total byte size of file then calculate a byte offset from the end to start reading at. Can use an estimate based on line length times how many lines we're reading.
- Start reading at offset, then search for line termination character (CR or CR LF depending on OS)
- When we have a line, add it to the beginning of a circular linked list or deque structure. Both of these are fast and efficient for adding to the beginning of a list.
  The list length is set by how many lines we are reading. A circular linked list we just keep writing round and round. A regular linked list / deque structure, we just pop 
  off end of the list and insert into the front if it was full. 
- If we somehow didn't hit our max log entries to read, we need to create a another offset and continue going back further. Starting at the offset and ending at our first new line encounter byte.
- At the end, we will have  a nice list with log entries in reverse order, capped at our max log size to return.


### Caching

I used a HashMap as a basic in memory cache structure. For production scaling, could evaluate using other in memory cache frameworks like MemCache, Redis, Hazelcast, etc.
I like this solution of every machine having a local cache of it's file logs and not distributed b/c if we have a lot of log collectors in a cluster, a shared distributed cache could get quite large.
And we don't care about availability if a machine goes down that's offered int he distrubted model, bc we need the file on that machine to watch for file changes anyways.

### Application Framework

I used Spring Bootstrap for the app level framework. This allows REST WS, MVC, and an Apache Web Server capabilities in a small distributable package

### Web Service

Simple RESTful WS design. 
>/cribl/log/tail?filename=[filename]&numEntries=[number]&filter=[text to filter each line for]

I chose to use Request Query Parameters instead of a Request Body via HTTP POST since this particular request is simple and it makes testing easier. <br/>
Also URIs are usually logged in web servers, proxies, load balancers, etc. so this makes auditing in production easier.

### Security

Basic HTTP Auth. UN/PW passed via HTTP header by client and checked server side. <br/>
For production scaling, auth service can be swapped out for LDAP, SAML 2.0, or OAuth

### Validation / Input Sanitization

Sanitize / Validate file name and filter input strings. File name validation is especially important as a malicious user could escape out of
default directory and display sensitive information with `../../someimportantfile.txt` semantics.

### Configuration

Used properties defined in `/resources/application.properties` to make application config changes without having to update code. <br/>
WS credentials are currently stored in plaintext prop file. Normally, this wouldn't be checked into source control and would be in a secure file location on prod machines.

>logging.level.com.cribl=DEBUG <br/>
com.cribl.logcollector.filepath=logs/ <br/>
com.cribl.logcollector.ws.username=cribl <br/>
com.cribl.logcollector.ws.password=password <br/>
