# Cribl Log Collector

## Change log
- [x] Created a lower level file watcher implementation that works with huge files with much more performance. It reads into the end of a file backwards, using a byte offset and buffer until we hit max entries to tail count. **Tests show tailing 1.6 GB file tails in 2 ms and JVM 170 MB mem usage total**.
- [ ] Implement extra credit primary / secondary cluster design described in Sys Design section.

## Usage
In the main directory run `./mvnw spring-boot:run` to start application server.<br/>
`mvnw` is a Maven wrapper which is our build automation tool and dependency manager. The wrapper makes it easier so you don't need to install Maven on your system.

New log files can go in /logs folder. <br/>
Config changes can be made to `/resources/application.properties`

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
- Unit Tests: 
  - Use `./mvnw test`. This will run all unit tests

Sample login page if testing HTTP GET through a web browser:

![image](https://github.com/paulsena/Cribl-Log-Collector-Interview/assets/826073/2e359988-97e5-489a-9401-5600ef926ea0)

After that, you will get the JSON response returned directly in the browser due to my serializer settings in the MVC app.

For large text files, I used publically available ones at: https://github.com/logpai/loghub?tab=readme-ov-file <br/>
I used a sample Hadoop log file 1.6GB in size, available for [download here](https://zenodo.org/records/8196385/files/HDFS_v1.zip?download=1)

### API Schema

**Request**
`http://localhost:8080/cribl/log/tail?fileName=<filename>&numEntries=<number>&filter=<text to filter each line for>`
```
fileName: Any alphanumeric character, space, or period punctuation
numEntries: Any value from 1 to 100. The upper limit is configurable via config properties. Limits malicious user potentially maxing out value and running out of JVM memory on very large files.
filter: A string keyword or search term to search for. Same alphanumeric and space character restrictions 
```

**Possible HTTP Responses**

HTTP 200 OK
```
{
  "logEntries": [
    "Log line 7",
    "Log line 6",
    "Log line 5"
  ],
  "filterUsed": "Log Line"
}
```

HTTP 400 Bad Request
```
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "File name or filter input is invalid. Only alphanumeric characters, numbers, spaces, and periods are allowed.",
  "instance": "/cribl/log/tail"
}
```

HTTP 404 Not Found
```
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "File does not exist on server: data/tester.txt",
  "instance": "/cribl/log/tail"
}
```

HTTP 429 Too Many Requests
```
{
  "type": "about:blank",
  "title": "Too Many Requests",
  "status": 429,
  "detail": "Reached maximum number of file watchers: 2",
  "instance": "/cribl/log/tail"
}
```

<br/>

## Future System Design

**Primary/Secondary Log Proxy**

In the questionaire form, it was posed for extra credit to develop a solution for a leader and follower cluster setup where one app server could be queried for log files and it would subsequently requests the log from multiple other secondary servers. <br/>
I ran out of time to implement this but I'll try to get to it within the next day or two for fun and issue a Pull Request!

Here would be my potential design:

  - Use a shared distributed message queue (like Kafka or RabbitMQ) to communicate that a new node has joined the group. Communication would be pub/sub done on a preset topic.
  - When a new log machine starts up, it broadcasts a register message out to the topic msg queue with it's URL server address (pub)
  - Each machine processes this message (sub) and saves it into an in memory list of machines
  - Expose a new endpoint in the shared app code called /primary/.. with the same follow URI for tailing a file. So example:`/primary/cribl/log/tail?filename=test.txt`
  - When the primary endpoint is called, it simply looks into it's in memory list of registered machines, and proxies an HTTP Get request to each of them. The response structure of the API could be updated to include server name and tail list for each.

This simple solution provides vault tolerance, load balancing and redundancy. Every machine has the full list of servers in the cluster and the ability to act as the primary proxy server if requested by the client. 
A load balancer could easily be put in front of the web server to round robin primary requests to all machines in the cluster to distribute load nicely at scale.
  
## System Design

To be discussed in later rounds of interviews but here was my thought process:

### File Reading

I used Java 8 IO Streams here which are part of the core JDK package.
They are very performant on large files (multiple gigabyte range) because they are lazily evaluated until the line is read.
I open the file with streams then reverse it, then read one line at a time until we hit the maximum requested log lines. <br/>
~~I was able to tail a 1.6 GB file in 6.4 seconds with this implementation.~~

**Update**: Implemented a new byte seeking file watcher that reads the **1.6 GB file in 2 ms.**

![image](https://github.com/paulsena/Cribl-Log-Collector-Interview/assets/826073/93716bf1-42af-4fdf-8ac9-c72d22d44604)
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

Sanitize / Validate file name and filter input strings. **File name validation is especially important** as a malicious user could escape out of
default directory and display sensitive information with `../../someimportantfile.txt` or `/sys/..` semantics.
Due to that, I limit the use of any paths in the file name. Only alphanumeric characters, spaces, and periods are allowed.
If you would like to change the  base directory, update the config file. A later design of this could have the config file use a whitelist of multiple directories to allow.

### Configuration

Used properties defined in `/resources/application.properties` to make application config changes without having to update code. <br/>
WS credentials are currently stored in plaintext prop file. Normally, this wouldn't be checked into source control and would be in a secure file location on prod machines.

```
server.error.include-message=always
logging.level.com.cribl=DEBUG
com.cribl.logcollector.filepath=logs/
com.cribl.logcollector.ws.username=cribl
com.cribl.logcollector.ws.password=password
com.cribl.logcollector.maxTailLines=100
```

### Unit Tests

I used the JUnit framework which lets me run all my tests in one suite package. Later on Mockito can be used for implementation mocking.

For production, a Continuous Integration hook should be setup so that we auto run all regression tests after each commit, deploy, etc.

Integration tests could be written for the exposed webservice to ensure schema contract doesn't change
