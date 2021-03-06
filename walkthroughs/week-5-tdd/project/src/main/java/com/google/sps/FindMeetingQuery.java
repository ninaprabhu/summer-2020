// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.sps;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;

/* Returns list of time ranges where specified people are free for specified time. */
public final class FindMeetingQuery {
  public Collection<TimeRange> query(Collection<Event> events, MeetingRequest request) {
    ArrayList<String> allAttendees = new ArrayList<>(request.getAttendees());
    allAttendees.addAll(new ArrayList<>(request.getOptionalAttendees())); // Treat all attendees as mandatory.
    MeetingRequest requestOptional = new MeetingRequest(allAttendees, request.getDuration());
    Collection<TimeRange> result = queryHelper(events, requestOptional);
    if (result.size() > 0 || request.getAttendees().size() == 0) {
        return result; //We found times that worked or there are no mandatory attendees.
    }
    return queryHelper(events, request); 
  }

  /* Returns a collection of TimeRanges that satisfy the people and duration needed in the request. */
  private Collection<TimeRange> queryHelper(Collection<Event> events, MeetingRequest request) {
    if (request.getDuration() > TimeRange.END_OF_DAY) {
        return new ArrayList<>(); // If the request is too long, return an empty list because this is impossible to accommodate.
    }
    ArrayList<TimeRange> badTimes = makeBadTimes(events, request);
    ArrayList<TimeRange> goodTimes = makeGoodTimes(request, badTimes);
    return goodTimes;
  }

  /* Returns a collection of TimeRanges during which at least one person in the request is busy. */
  private ArrayList<TimeRange> makeBadTimes(Collection<Event> events, MeetingRequest request) {
    ArrayList<TimeRange> badTimes = new ArrayList<>();
    for (Event event : events) {
      if (!Collections.disjoint(event.getAttendees(), request.getAttendees())) {
        badTimes.add(event.getWhen());
      }
    }
    Collections.sort(badTimes, TimeRange.ORDER_BY_START);
    return badTimes;
  }
  
  /* Returns a collection of TimeRanges that is the opposite of those specified in badTimes. */
  private ArrayList<TimeRange> makeGoodTimes(MeetingRequest request, ArrayList<TimeRange> badTimes) {
    ArrayList<TimeRange> badTimesCopy = badTimes;
    ArrayList<TimeRange> goodTimes = new ArrayList<>();
    if (badTimes.size() == 0) { // We are free the whole day - no times are off limits.
      goodTimes.add(TimeRange.fromStartEnd(TimeRange.START_OF_DAY, TimeRange.END_OF_DAY + 1, false));
      return goodTimes;
    }

    int firstDuration = badTimes.get(0).start();
    if (firstDuration > 0) {
        goodTimes.add(TimeRange.fromStartDuration(TimeRange.START_OF_DAY, firstDuration));
    }
    
    // Two for-loops to avoid modifying array while looping over it.
    for (int i = 1; i < badTimesCopy.size(); i++){
        TimeRange first = badTimesCopy.get(i - 1);
        TimeRange second = badTimesCopy.get(i);
        if (first.contains(second)) { // Replace second with first to extend end time and avoid later mistakes.
            badTimes.set(i, TimeRange.fromStartDuration(first.start(), first.duration()));
        }
    }

    for (int i = 1; i < badTimes.size(); i++){
        TimeRange first = badTimes.get(i - 1);
        TimeRange second = badTimes.get(i);
        if (!first.overlaps(second) && // There is space between the two events.
            (second.start() - first.end()) >= request.getDuration()) { // The space between the two events is long enough.
            goodTimes.add(TimeRange.fromStartEnd(first.end(), second.start(), false));
        }
    }

    int end = badTimes.get(badTimes.size() - 1).end();
    if (TimeRange.END_OF_DAY + 1 > end) {
        goodTimes.add(TimeRange.fromStartEnd(end, TimeRange.END_OF_DAY + 1, false));
    }
    return goodTimes;
  }
}
