# UI

Initial setup
$ npm create vite@latest

$ cd frontend
$ npm install
$ npm run dev

$ sbt
$ project frontend
$ ~fastLinkJS

### Total YOLO mode!

That might change in the future. Currently, the frontend is on experimental mode.

### Validation and other business rules

Virtually all the business logic should be defined on the server side despite the trade-offs.

 * Defining business rules (validation included) in one place (backend) will speed up development and facilitate maintainance
 * We don't expect a heavy load on the server for a while, so frontend optimisation is not an issue
 * The server should be able to reply fast enough, so user experience doesn't get compromised

Field validation and formating in the FE should still occur when the logic is straightforward and should positivitely impact experience:
 * Mandatory fields
 * File extensions (preventing sending the part or whole payload to the server so validation occurs)
 * Numeric fields (prevent characters, format dates)
 * E-mail (checking for @ should do)

### Resources
 * [Getting started with Scala js, Laminar and Vite](https://www.youtube.com/watch?v=hWUAVrNj65c)
 * https://github.com/sjrd/scalajs-sbt-vite-laminar-chartjs-example
 * [Laminar](https://laminar.dev/resources)
 * [Waypoint](https://github.com/raquo/Waypoint)
 * [Waypoint/Routers and more](https://blog.indoorvivants.com/2022-03-07-twotm8-part-5-building-the-frontend)
