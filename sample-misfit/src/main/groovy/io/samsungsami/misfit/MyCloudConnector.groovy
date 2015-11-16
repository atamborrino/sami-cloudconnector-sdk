package io.samsungsami.misfit

import org.scalactic.*
import org.joda.time.format.DateTimeFormat
import org.joda.time.DateTime
import org.joda.time.DateTimeZone;

import groovy.transform.CompileStatic
import groovy.transform.ToString
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import scala.Option

import com.samsung.sami.cloudconnector.api.*

import static java.net.HttpURLConnection.*

import org.joda.time.format.ISODateTimeFormat

//@CompileStatic
class MyCloudConnector extends CloudConnector {
	static final mdateFormat = DateTimeFormat.forPattern("yyyy-MM-dd").withZoneUTC()
	static final String endpoint = "https://api.misfitwearables.com/move/resource/v1/user/"

	JsonSlurper slurper = new JsonSlurper()

	@Override
	def Or<RequestDef, Failure> signAndPrepare(Context ctx, RequestDef req, DeviceInfo info, Phase phase){
		new Good(req.addHeaders(["access_token": info.credentials.token]))
	}

	@Override
	def Or<List<RequestDef>, Failure> subscribe(Context ctx, DeviceInfo info) {
		new Good([new RequestDef(endpoint + "me/profile")])
	}

	@Override
	def Or<Option<DeviceInfo>,Failure> onSubscribeResponse(Context ctx, RequestDef req,  DeviceInfo info, Response res) {
		def json = slurper.parseText(res.content)
		new Good(Option.apply(info.withExtId(json.userId)))
	}

	@Override
	def Or<NotificationResponse, Failure> onNotification(Context ctx, RequestDef inReq) {
		def json = slurper.parseText(inReq.content)
		if (json.SubscribeURL) {
			String url = json.SubscribeURL
			new Good(new NotificationResponse([new ThirdPartyNotification(Empty.deviceSelector(), [new RequestDef(url)])]))
		} else {
			new Good(new NotificationResponse(extractNotification(ctx, json.Message)))
		}
	}

	def extractNotification(Context ctx, String str) {
		slurper.parseText(str).findResults{ collection ->
			def kind = collection.type
			def extId = collection.ownerId
			def objId = collection.id

			if (kind == "profiles") {
				null
			} else {
			    def sub
				switch(kind) {
					case "devices" : sub = "/device/"; break
					case "goals" : sub = "/activity/goals/"; break
					case "sessions" : sub = "/activity/sessions/"; break
					case "sleeps" : sub = "/activity/sleeps/"; break
				}
				def reqs = [new RequestDef(endpoint + extId + sub + objId)]
				if (kind == 'goals') {
					def today = mdateFormat.print(new DateTime(ctx.now()))
					reqs = reqs + new RequestDef(endpoint + extId + "/activity/summary").withQueryParams(["start_date" : today, "end_date" : today, "detail": "true"])
				}
				new ThirdPartyNotification(new ByExternalDeviceId(extId), reqs)
			}
		}
	}

	@Override
	def Or<List<Event>, Failure> onFetchResponse(Context ctx, RequestDef req, DeviceInfo info, Response res) {
		switch(res.status) {
			case HTTP_OK:
				def content = res.content.trim()
				if (content == "" || content == "OK") {
					ctx.debug("ignore response valid respond: '${res.content}'")
					return new Good(Empty.list())
				} else if (res.contentType.startsWith("application/json")) {
					return new Good(parseSingleEntry(slurper.parseText(content), findGroup(req), ctx.now()))
				}
				return new Bad(new Failure("unsupported response ${res} ... ${res.contentType} .. ${res.contentType.startsWith("application/json")}"))
			default:
				return new Bad(new Failure("http status : ${res.status} is not OK (${HTTP_OK})"))
		}
	}

	def String findGroup(RequestDef req) {
		def group
		def url = req.url
		if (url.indexOf("/device/") > 0) group = "device"
		if (url.indexOf("/activity/goals/") > 0) group = "goals"
		if (url.indexOf("/activity/sessions/") > 0) group = "sessions"
		if (url.indexOf("/activity/sleeps/") > 0) group = "sleeps"
		if (url.indexOf("/activity/summary") > 0) group = "summary"
		return group
	}

	def transformJson(json, group) {
		def res = []
		if (group != "summary") {
			res = res + ((group)? [(group) : json] : json)
		}
		if (json.sleepDetails) {
			res = res + json.sleepDetails.collect{
				["sleepDetails": ["id": json.id] + it ]
			}
		}
		if (json.summary) {
			//TODO support or reject non-array
			res = res + json.summary.collect{
				["summary": it ]
			}
		}
		res
	}

	def parseSingleEntry(json, group, defaultTs) {
		transformJson(json, group).collect{
			def ts = extractTimestamp((group && !it.empty)? it.values()[0] : it, defaultTs)
			new Event(ts, JsonOutput.toJson(it))
		}
	}

	def isSameDay(DateTime d1, DateTime d2) {
		(d1.year== d2.year) && (d1.dayOfYear ==  d2.dayOfYear)
	}

	def getTimestampOfTheEndOfTheDay(DateTime date) {
		date.minusMillis((date.millisOfDay().get() + 1 )).plusDays(1)
	}

	/**
	 * Since we recover the summary data of the day, we want to have a meaningful timestamp from source:
	 * If the date is not today : the day is finished. We set the source timestamp to the last second of this past day.
	 * If the date is today : the day is not finished, data can continue to evolve for the day, we set the timestamp to now()
	 */
	def getTimestampFromDate(DateTime date, DateTimeZone dtz = DateTimeZone.UTC) {
		def now = new DateTime(dtz).toDateTime(dtz)
		def returnedDate = isSameDay(date, now)? now : getTimestampOfTheEndOfTheDay(date)
		returnedDate.getMillis()
	}

	def extractTimestamp(json, defaultTs) {
		if (json.date) {
			def tz = (json.timeZoneOffset)? DateTimeZone.forOffsetHours(json.timeZoneOffset as int) : DateTimeZone.UTC
			getTimestampFromDate(DateTime.parse(json.date, mdateFormat.withZone(tz)), tz)
		} else if (json.startTime){
			DateTime.parse(json.startTime, ISODateTimeFormat.dateTimeNoMillis()).getMillis()
		} else if (json.datetime){
			DateTime.parse(json.datetime, ISODateTimeFormat.dateTimeNoMillis()).getMillis()
		} else {
			defaultTs
		}
	}
}