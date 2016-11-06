package com.couchface.sports.CollegeBasketballStatsCrawler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.text.WordUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.couchface.sports.basketballstats.entity.Assist;
import com.couchface.sports.basketballstats.entity.Block;
import com.couchface.sports.basketballstats.entity.FieldGoal;
import com.couchface.sports.basketballstats.entity.Foul;
import com.couchface.sports.basketballstats.entity.FreeThrow;
import com.couchface.sports.basketballstats.entity.Game;
import com.couchface.sports.basketballstats.entity.GameEvent;
import com.couchface.sports.basketballstats.entity.Lineup;
import com.couchface.sports.basketballstats.entity.Player;
import com.couchface.sports.basketballstats.entity.Rebound;
import com.couchface.sports.basketballstats.entity.ShotType;
import com.couchface.sports.basketballstats.entity.ShotTypeDomain;
import com.couchface.sports.basketballstats.entity.Steal;
import com.couchface.sports.basketballstats.entity.Substitution;
import com.couchface.sports.basketballstats.entity.Team;
import com.couchface.sports.basketballstats.entity.Timeout;
import com.couchface.sports.basketballstats.entity.TimeoutType;
import com.couchface.sports.basketballstats.entity.TimeoutTypeDomain;
import com.couchface.sports.basketballstats.entity.Turnover;

public class GameParser {
	
	public static final String BOX_URL_PREFIX = "http://stats.ncaa.org/game/box_score/";
	public static final String PERIOD_VARIABLE = "?period_no=";
	public static final String PLAY_BY_PLAY = "http://stats.ncaa.org/game/play_by_play/";
	
	private int regulationPeriodNumber;
	private int regulationPeriodLength;
	private int overtimePeriodLength;
	
	private int currentTime = 0;
	
	private Game currentGame;
	private Lineup currentLineup = new Lineup();
	private Lineup currentHomeLineup = new Lineup();
	private Lineup currentAwayLineup = new Lineup();
	private ArrayList<Player> awayRoster;
	private ArrayList<Player> homeRoster;
	
	public GameParser()  {
		
	}
	
	public GameParser(int periods, int regulationLength, int overtimeLength) {
		this.regulationPeriodNumber = periods;
		this.regulationPeriodLength = regulationLength;
		this.overtimePeriodLength = overtimeLength;
	}
	
	public Game parseGame(int gameId) throws IOException{
		try {
			this.parseGameMetaData(gameId);
		} catch (IOException e) {
			System.out.println("Failed to connect with id, returning null.");
			throw e;
		}
		
		System.out.println("Now parsing game from " + this.currentGame.getGameTime() + " between " + this.currentGame.getHomeTeam().getSchoolName() +
				" and " + this.currentGame.getAwayTeam().getSchoolName() + ".");
		
		this.parseRosters();
		
		this.parsePlayByPlay();
		System.out.println("Finished Parsing Game");
		
		return this.currentGame;
	}
	
	private void parseGameMetaData(int gameId) throws IOException {
		
		Document doc = Jsoup.connect(PLAY_BY_PLAY + gameId).get();
		this.currentGame = new Game();
		this.currentGame.setGameId(gameId);
		Elements tables = doc.select("table");
		Element nameBox = tables.get(0);
		Element dateLocation = tables.get(2);
		Element officials = tables.get(3);
		
		this.parseTeamNames(nameBox);
		this.parseDateLocation(dateLocation);
		this.parseOfficials(officials);
	}
	
	private void parseTeamNames(Element table) {
		Elements rows = table.select("tr");
		
		Element awayCell = rows.get(1).select("td").get(0);
		Element homeCell = rows.get(2).select("td").get(0);
		
		Team awayTeam = new Team();
		awayTeam.setSchoolName(awayCell.select("a").get(0).text());
		this.currentGame.setAwayTeam(awayTeam);
		
		Team homeTeam = new Team();
		homeTeam.setSchoolName(homeCell.select("a").get(0).text());
		this.currentGame.setHomeTeam(homeTeam);
	}
	
	private void parseDateLocation(Element table) {
		Elements rows = table.select("tr");
		
		Element dateCell = rows.get(0).select("td").get(1);
		this.currentGame.setGameTime(dateCell.text());
		
		Element locationCell = rows.get(1).select("td").get(1);
		this.currentGame.setArena(locationCell.text());
	}
	
	private void parseOfficials(Element table) {
		Element officialCell = table.select("tr").get(0).select("td").get(1);
		String allOfficials = officialCell.text();
		
		String[] officialArray = allOfficials.split(",\\s*");
		this.currentGame.setOfficialOne(officialArray[0]);
		this.currentGame.setOfficialTwo(officialArray[1]);
		this.currentGame.setOfficialThree(officialArray[2]);
	}
	
	private void parseRosters() throws IOException {
		Document doc = Jsoup.connect(BOX_URL_PREFIX + this.currentGame.getGameId()).get();
		
		this.awayRoster = this.parseNamesFromRows(doc.select("table").get(4).select("tr"),this.currentGame.getAwayTeam(),false);
		this.homeRoster = this.parseNamesFromRows(doc.select("table").get(5).select("tr"),this.currentGame.getHomeTeam(),false);
		
	}
	
	private ArrayList<Player> parseNamesFromRows(Elements rows, Team team, boolean startersOnly) {
		ArrayList<Player> list = new ArrayList<Player>();
		int max = (rows.size() - 2);
		if (startersOnly) {
			max = 7;
		}
		for (int i = 2; i < max; i++) {
			String playerName = rows.get(i).select("a").get(0).text();
			String[] names = playerName.split(",\\s*");
			list.add(new Player(WordUtils.capitalizeFully(names[1]),WordUtils.capitalizeFully(names[0])));
			list.get(i-2).setTeam(team);
		}
		return list;
	}
	
	private void parsePlayByPlay() throws IOException {
		int period = 1;
		
		Document playByPlay = Jsoup.connect(PLAY_BY_PLAY + this.currentGame.getGameId()).get();
		Elements tables = playByPlay.select("table");
		
		while((period*2 + 3) < tables.size()) {	//while loop that goes through each period of play
			if (period > 1) {
				this.currentAwayLineup.setEndTime(parseEventTime("0:00"));
				this.currentHomeLineup.setEndTime(parseEventTime("0:00"));
				this.currentGame.getLineups().add(this.currentAwayLineup);
				this.currentGame.getLineups().add(this.currentHomeLineup);
			}
			
			this.parsePeriodStarters(period);
			
			for(int i = 1; i < (tables.get((period * 2) + 3).select("tr").size() - 1); i++){ //for loop that goes through each event
				GameEvent newEvent = this.parseEvent(tables.get((period * 2) + 3).select("tr").get(i));
				newEvent.setPeriod(period);
				this.currentGame.getEvents().add(newEvent);
			}
			
			period++;
		}
		
	}
	
	private GameEvent parseEvent(Element eventRow)	{
		GameEvent event;
		
		boolean isHomeEvent;
		String text;
		
		if (eventRow.select("td").get(1).text().isEmpty()){	//checking if it's an away team event
			isHomeEvent = true;
			text = eventRow.select("td").get(3).text();	//set the text to the home team text
		} else {
			isHomeEvent = false;
			text = eventRow.select("td").get(1).text();	//set the text to the away team text
		}
		text = text.replaceAll("<\\/?[bi]>", ""); //eliminates pesky bold and italics tags
		event = this.parseEventText(text, isHomeEvent);
		
		if(isHomeEvent) {
			event.setTeam(this.currentGame.getHomeTeam());
		} else {
			event.setTeam(this.currentGame.getAwayTeam());
		}
		event.setEventText(text);
		event.setGame(this.currentGame);
		this.currentTime = this.parseEventTime(eventRow.select("td").get(0).text());
		event.setTime(currentTime);
		event.setHomeLineup(this.currentLineup);
		event.setAwayLineup(this.currentLineup);
		return event;
	}
	
	private GameEvent parseEventText(String text, boolean isHomeEvent) {
		Player eventPlayer = this.parsePlayer(text);
		if (eventPlayer != null) {
			if (isHomeEvent) {
				eventPlayer.setTeam(this.currentGame.getHomeTeam());
				eventPlayer = this.convertToRosteredPlayer(eventPlayer, this.homeRoster);
			} else {
				eventPlayer.setTeam(this.currentGame.getAwayTeam());
				eventPlayer = this.convertToRosteredPlayer(eventPlayer, this.awayRoster);
			}
		}
		
		int eventBegin = this.findFirstLowercaseOrNumericIndex(text);
		if (!(text.charAt(eventBegin - 1) == ' ')) {
			eventBegin--;
		}
		
		return parseSubEvent(text.substring(eventBegin), eventPlayer);
	}
	
	private GameEvent parseSubEvent(String text, Player eventPlayer) {
		GameEvent previousEvent;
		if(this.currentGame.getEvents().isEmpty()){
			previousEvent = null;
		} else {
			previousEvent = this.currentGame.getEvents().get(this.currentGame.getEvents().size()-1);
		}
		boolean isTeamEvent = (eventPlayer == null);
		switch (text){
			case "Turnover":
				Turnover turnover = new Turnover();
				turnover.setTeamTurnover(isTeamEvent);
				turnover.setPlayer(eventPlayer);
				return turnover;
			case "Steal":
				Steal steal = new Steal();
				steal.setPlayer(eventPlayer);
				steal.setTurnover((Turnover) previousEvent);
				return steal;
			case "Blocked Shot":
				Block block = new Block();
				block.setBlocker(eventPlayer);
				block.setFieldGoalAttempt((FieldGoal) previousEvent);
				return block;
			case "Assist":
				Assist assist = new Assist();
				assist.setPasser(eventPlayer);
				assist.setFieldGoal((FieldGoal) previousEvent);
				return assist;
			case "Deadball Rebound":
				Rebound dbrebound = new Rebound();
				dbrebound.setRebounder(eventPlayer);
				dbrebound.setTeamRebound(isTeamEvent);
				dbrebound.setType(null);
				return dbrebound;
			case "Offensive Rebound":
				Rebound orebound = new Rebound();
				orebound.setRebounder(eventPlayer);
				orebound.setTeamRebound(isTeamEvent);
				orebound.setType(Rebound.OFFENSIVE);
				return orebound;
			case "Defensive Rebound":
				Rebound drebound = new Rebound();
				drebound.setRebounder(eventPlayer);
				drebound.setTeamRebound(isTeamEvent);
				drebound.setType(Rebound.DEFENSIVE);
				return drebound;
			case "Commits Foul":
				Foul foul = new Foul();
				foul.setCoachFoul(false);//always false for now I guess
				foul.setTeamFoul(isTeamEvent);
				foul.setFouler(eventPlayer);
				return foul;
			case "Media Timeout":
				Timeout mtimeout = new Timeout();
				mtimeout.setType(getTimeoutType(TimeoutType.MEDIA));
				return mtimeout;
			case "30 Second Timeout":
				Timeout timeout30 = new Timeout();
				timeout30.setType(getTimeoutType(TimeoutType.THIRTY_SEC));
				return timeout30;
			case "Timeout":
			case "Team Timeout":
				Timeout ftimeout = new Timeout();
				ftimeout.setType(getTimeoutType(TimeoutType.FULL));
				return ftimeout;
			case "made Free Throw":
				FreeThrow mdfreeThrow = new FreeThrow();
				mdfreeThrow.setMade(true);
				mdfreeThrow.setShooter(eventPlayer);
				if (previousEvent instanceof Rebound){
					mdfreeThrow.setFoul((Foul) this.currentGame.getEvents().get(this.currentGame.getEvents().size() - 3));
				}else if (previousEvent instanceof FreeThrow){
					mdfreeThrow.setFoul(((FreeThrow) previousEvent).getFoul());
				}else if (previousEvent instanceof Timeout){
					mdfreeThrow.setFoul((Foul) this.currentGame.getEvents().get(this.currentGame.getEvents().size() - 2));
				}else {
					mdfreeThrow.setFoul((Foul) previousEvent);
				}
				return mdfreeThrow;
			case "missed Free Throw":
				FreeThrow msfreeThrow = new FreeThrow();
				msfreeThrow.setMade(false);
				msfreeThrow.setShooter(eventPlayer);
				if (previousEvent instanceof Rebound){
					msfreeThrow.setFoul((Foul) this.currentGame.getEvents().get(this.currentGame.getEvents().size() - 3));
				}else if (previousEvent instanceof FreeThrow){
					msfreeThrow.setFoul(((FreeThrow) previousEvent).getFoul());
				}else {
					msfreeThrow.setFoul((Foul) previousEvent);
				}
				return msfreeThrow;
			case "made Tip In":
				FieldGoal mdtipin = new FieldGoal();
				mdtipin.setShooter(eventPlayer);
				mdtipin.setMade(true);
				mdtipin.setType(getShotType(ShotType.TIP_IN));
				return mdtipin;
			case "missed Tip In":
				FieldGoal mstipin = new FieldGoal();
				mstipin.setShooter(eventPlayer);
				mstipin.setMade(false);
				mstipin.setType(getShotType(ShotType.TIP_IN));
				return mstipin;
			case "made Dunk":
				FieldGoal mddunk = new FieldGoal();
				mddunk.setShooter(eventPlayer);
				mddunk.setMade(true);
				mddunk.setType(getShotType(ShotType.DUNK));
				return mddunk;
			case "missed Dunk":
				FieldGoal msdunk = new FieldGoal();
				msdunk.setShooter(eventPlayer);
				msdunk.setMade(false);
				msdunk.setType(getShotType(ShotType.DUNK));
				return msdunk;
			case "made Layup":
				FieldGoal mdlayup = new FieldGoal();
				mdlayup.setShooter(eventPlayer);
				mdlayup.setMade(true);
				mdlayup.setType(getShotType(ShotType.LAYUP));
				return mdlayup;
			case "missed Layup":
				FieldGoal mslayup = new FieldGoal();
				mslayup.setShooter(eventPlayer);
				mslayup.setMade(false);
				mslayup.setType(getShotType(ShotType.LAYUP));
				return mslayup;
			case "made Two Point Jumper":
				FieldGoal mdjumper2 = new FieldGoal();
				mdjumper2.setShooter(eventPlayer);
				mdjumper2.setMade(true);
				mdjumper2.setType(getShotType(ShotType.TWO_POINT_JUMPER));
				return mdjumper2;
			case "missed Two Point Jumper":
				FieldGoal msjumper2 = new FieldGoal();
				msjumper2.setShooter(eventPlayer);
				msjumper2.setMade(false);
				msjumper2.setType(getShotType(ShotType.TWO_POINT_JUMPER));
				return msjumper2;
			case "made Three Point Jumper":
				FieldGoal mdjumper3 = new FieldGoal();
				mdjumper3.setShooter(eventPlayer);
				mdjumper3.setMade(true);
				mdjumper3.setType(getShotType(ShotType.THREE_POINT_JUMPER));
				return mdjumper3;
			case "missed Three Point Jumper":
				FieldGoal msjumper3 = new FieldGoal();
				msjumper3.setShooter(eventPlayer);
				msjumper3.setMade(false);
				msjumper3.setType(getShotType(ShotType.THREE_POINT_JUMPER));
				return msjumper3;
			case "Enters Game":
				Substitution subEnter = new Substitution();
				subEnter.setPlayer(eventPlayer);
				subEnter.setEnterFlag(Substitution.getEnter());
				this.addPlayerToLineup(eventPlayer);
				return subEnter;
			case "Leaves Game":
				Substitution subExit = new Substitution();
				subExit.setPlayer(eventPlayer);
				subExit.setEnterFlag(Substitution.getExit());
				this.removePlayerFromLineup(eventPlayer);
				return subExit;
			default:
				return null;
		}
	}

	private void addPlayerToLineup(Player player) {
		if (player.getTeam().equals(currentGame.getHomeTeam())){
			if (this.currentHomeLineup.getPlayers().size() == 5) {
				this.currentHomeLineup.setEndTime(currentTime);
				this.currentGame.getLineups().add(currentHomeLineup);
				this.currentHomeLineup = this.currentHomeLineup.shallowCopy();
				this.currentHomeLineup.setStartTime(currentTime);
			}
			this.currentHomeLineup.getPlayers().add(this.convertToRosteredPlayer(player,this.homeRoster));
			
		}else {
			if (this.currentAwayLineup.getPlayers().size() == 5) {
				this.currentAwayLineup.setEndTime(currentTime);
				this.currentGame.getLineups().add(currentAwayLineup);
				this.currentAwayLineup = this.currentAwayLineup.shallowCopy();
				this.currentAwayLineup.setStartTime(currentTime);
			}
			this.currentAwayLineup.getPlayers().add(this.convertToRosteredPlayer(player,this.awayRoster));
		}
	}
	
	private void removePlayerFromLineup(Player player) {
		if (player.getTeam().equals(currentGame.getHomeTeam())){
			if (this.currentHomeLineup.getPlayers().size() == 5) {
				this.currentHomeLineup.setEndTime(currentTime);
				this.currentGame.getLineups().add(currentHomeLineup);
				this.currentHomeLineup = this.currentHomeLineup.shallowCopy();
				this.currentHomeLineup.setStartTime(currentTime);
			}
			for (int i = 0; i < this.currentHomeLineup.getPlayers().size(); i++) {
				if (this.currentHomeLineup.getPlayers().get(i).getFirstName().equalsIgnoreCase(player.getFirstName()) && this.currentHomeLineup.getPlayers().get(i).getLastName().equalsIgnoreCase(player.getLastName())) {
					this.currentHomeLineup.getPlayers().remove(i);
					break;
				}
			}
		}else {
			if (this.currentAwayLineup.getPlayers().size() == 5) {
				this.currentAwayLineup.setEndTime(currentTime);
				this.currentGame.getLineups().add(currentAwayLineup);
				this.currentAwayLineup = this.currentAwayLineup.shallowCopy();
				this.currentAwayLineup.setStartTime(currentTime);
			}
			for (int i = 0; i < this.currentAwayLineup.getPlayers().size(); i++) {
				if (this.currentAwayLineup.getPlayers().get(i).getFirstName().equalsIgnoreCase(player.getFirstName()) && this.currentAwayLineup.getPlayers().get(i).getLastName().equalsIgnoreCase(player.getLastName())) {
					this.currentAwayLineup.getPlayers().remove(i);
					break;
				}
			}
		}
	}
	
	/**
	 * 
	 * @param text The player's name, in Last,First form, with any capitalization. returns null if "TEAM"
	 * @return A Player object with a "fully capitalized" (see the WordUtils.capitalizeFully(str) method) first and last name.
	 */
	private Player parsePlayer(String text) {
		int endOfPlayer = this.findFirstLowercaseOrNumericIndex(text);
		if (text.charAt(endOfPlayer - 1) == ' ') {
			endOfPlayer = (endOfPlayer - 2);
		} else {
			endOfPlayer = (endOfPlayer - 3);
		}
		
		String playerName = text.substring(0, endOfPlayer + 1);
		if (playerName.equals("TEAM")){
			return null;
		}
		String[] names = playerName.split(",\\s*");
		
		return new Player(playerName = WordUtils.capitalizeFully(names[1]),playerName = WordUtils.capitalizeFully(names[0]));
	}
	
	/*
	 * I took this method one step further out because I like this part of it alone.
	 */
	private int findFirstLowercaseOrNumericIndex(String text) {
		for (int i = 0; i < text.length(); i++) {
			if (Character.toString(text.charAt(i)).matches("[a-z0-9?]")){
				return i;
			}
		}
		return -1;
	}

	private int parseEventTime(String timeText) {
		int time = 0;
		String[] timeSplit = timeText.split(":");
		time += (Integer.parseInt(timeSplit[0]) * 60);
		time += (Integer.parseInt(timeSplit[1]));
		return time;
	}
	
	private void parsePeriodStarters(int period) throws IOException {
		Document periodBox = Jsoup.connect(BOX_URL_PREFIX + this.currentGame.getGameId() + PERIOD_VARIABLE + period).get();
		this.currentLineup = new Lineup(this.currentGame);
		
		ArrayList<Player> awayStarters = this.parseNamesFromRows(periodBox.select("table").get(4).select("tr"),this.currentGame.getAwayTeam(),true);
		for (Player player : awayStarters) {
			player = this.convertToRosteredPlayer(player, this.awayRoster);
		}
		this.currentAwayLineup = this.currentLineup.shallowCopy();
		this.currentAwayLineup.getPlayers().clear();
		this.currentAwayLineup.getPlayers().addAll(awayStarters);
		
		ArrayList<Player> homeStarters = this.parseNamesFromRows(periodBox.select("table").get(5).select("tr"),this.currentGame.getAwayTeam(),true);
		for (Player player : homeStarters) {
			player = convertToRosteredPlayer(player, this.homeRoster);
		}
		this.currentHomeLineup = this.currentHomeLineup.shallowCopy();
		this.currentHomeLineup.getPlayers().clear();
		this.currentHomeLineup.getPlayers().addAll(homeStarters);
		
		this.currentHomeLineup.setPeriod(period);
		this.currentAwayLineup.setPeriod(period);
		if (period < 3) {
			this.currentHomeLineup.setStartTime(parseEventTime("20:00"));
			this.currentAwayLineup.setStartTime(parseEventTime("20:00"));
		} else {
			this.currentHomeLineup.setStartTime(parseEventTime("5:00"));
			this.currentAwayLineup.setStartTime(parseEventTime("5:00"));
		}
	}

	private Player convertToRosteredPlayer(Player player, List<Player> roster) {
		for (Player rostered : roster) {
			if (rostered.getFirstName().equals(player.getFirstName()) && rostered.getLastName().equals(player.getLastName())) {
				return rostered;
			}
		}
		return null;
	}
	
	private TimeoutTypeDomain getTimeoutType(TimeoutType type) {//TODO make this grab the persisted TimeoutTypeDomain
		return new TimeoutTypeDomain(type);
	}
	
	private ShotTypeDomain getShotType(ShotType type) {//TODO make this grab the persisted ShotTypeDomain
		return new ShotTypeDomain(type);
	}

	
}
